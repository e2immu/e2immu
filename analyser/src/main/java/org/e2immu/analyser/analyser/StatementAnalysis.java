/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.model.statement.SynchronizedStatement;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.resolver.Resolver;
import org.e2immu.analyser.util.AddOnceSet;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.AnnotationMode;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

@Container
public class StatementAnalysis extends AbstractAnalysisBuilder implements Comparable<StatementAnalysis>, HasNavigationData<StatementAnalysis> {

    public final Statement statement;
    public final String index;
    public final StatementAnalysis parent;
    public final boolean inSyncBlock;
    public final MethodAnalysis methodAnalysis;

    public final AddOnceSet<Message> messages = new AddOnceSet<>();
    public final NavigationData<StatementAnalysis> navigationData = new NavigationData<>();
    public final SetOnceMap<String, VariableInfoContainer> variables = new SetOnceMap<>();
    public final AddOnceSet<ObjectFlow> internalObjectFlows = new AddOnceSet<>();

    public final MethodLevelData methodLevelData = new MethodLevelData();
    public final StateData stateData = new StateData();
    public final FlowData flowData = new FlowData();
    public final AddOnceSet<String> localVariablesAssignedInThisLoop;
    public final SetOnce<Boolean> done = new SetOnce<>(); // if not done, there have been delays

    public StatementAnalysis(Primitives primitives,
                             MethodAnalysis methodAnalysis,
                             Statement statement, StatementAnalysis parent, String index, boolean inSyncBlock) {
        super(primitives, index);
        this.index = super.simpleName;
        this.statement = statement;
        this.parent = parent;
        this.inSyncBlock = inSyncBlock;
        this.methodAnalysis = Objects.requireNonNull(methodAnalysis);
        localVariablesAssignedInThisLoop = statement instanceof LoopStatement ? new AddOnceSet<>() : null;
    }

    public String toString() {
        return index + ": " + statement.getClass().getSimpleName();
    }

    @Override
    public int compareTo(StatementAnalysis o) {
        return index.compareTo(o.index);
    }

    @Override
    public NavigationData<StatementAnalysis> getNavigationData() {
        return navigationData;
    }

    public boolean inErrorState(String message) {
        boolean parentInErrorState = parent != null && parent.inErrorState(message);
        if (parentInErrorState) return true;
        return messages.stream().anyMatch(m -> m.message.contains(message));
    }

    public static StatementAnalysis startOfBlock(StatementAnalysis sa, int block) {
        return sa == null ? null : sa.startOfBlock(block);
    }

    private StatementAnalysis startOfBlock(int i) {
        if (!navigationData.blocks.isSet()) return null;
        List<Optional<StatementAnalysis>> list = navigationData.blocks.get();
        return i >= list.size() ? null : list.get(i).orElse(null);
    }

    @Override
    public StatementAnalysis followReplacements() {
        if (navigationData.replacement.isSet()) {
            return navigationData.replacement.get().followReplacements();
        }
        return this;
    }

    @Override
    public String index() {
        return index;
    }

    @Override
    public Statement statement() {
        return statement;
    }

    @Override
    public StatementAnalysis lastStatement() {
        if (flowData.isUnreachable()) {
            throw new UnsupportedOperationException("The first statement can never be unreachable");
        }
        return followReplacements().navigationData.next.get().map(statementAnalysis -> {
            if (statementAnalysis.flowData.isUnreachable()) {
                return this;
            }
            return statementAnalysis.lastStatement();
        }).orElse(this);
    }

    @Override
    public StatementAnalysis parent() {
        return parent;
    }

    @Override
    public void wireNext(StatementAnalysis newStatement) {
        navigationData.next.set(Optional.ofNullable(newStatement));
    }

    @Override
    public BiFunction<List<Statement>, String, StatementAnalysis> generator(EvaluationContext evaluationContext) {
        return (statements, startIndex) -> recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent(),
                statements, startIndex, false, inSyncBlock);
    }

    public static StatementAnalysis recursivelyCreateAnalysisObjects(
            Primitives primitives,
            MethodAnalysis methodAnalysis,
            StatementAnalysis parent,
            List<Statement> statements,
            String indices,
            boolean setNextAtEnd,
            boolean inSyncBlock) {
        Objects.requireNonNull(methodAnalysis);
        int statementIndex;
        if (setNextAtEnd) {
            statementIndex = 0;
        } else {
            // we're in the replacement mode; replace the existing index value
            int pos = indices.lastIndexOf(".");
            statementIndex = Integer.parseInt(pos < 0 ? indices : indices.substring(pos + 1));
        }
        StatementAnalysis first = null;
        StatementAnalysis previous = null;
        for (Statement statement : statements) {
            String iPlusSt = indices + "." + statementIndex;
            StatementAnalysis statementAnalysis = new StatementAnalysis(primitives, methodAnalysis, statement, parent, iPlusSt, inSyncBlock);
            if (previous != null) {
                previous.navigationData.next.set(Optional.of(statementAnalysis));
            }
            previous = statementAnalysis;
            if (first == null) first = statementAnalysis;

            int blockIndex = 0;
            List<Optional<StatementAnalysis>> analysisBlocks = new ArrayList<>();

            boolean newInSyncBlock = inSyncBlock || statement instanceof SynchronizedStatement;
            Structure structure = statement.getStructure();
            if (structure.haveStatements()) {
                StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent,
                        structure.statements, iPlusSt + "." + blockIndex, true, newInSyncBlock);
                analysisBlocks.add(Optional.of(subStatementAnalysis));
            } else {
                analysisBlocks.add(Optional.empty());
            }
            blockIndex++;
            for (Structure subStatements : structure.subStatements) {
                if (subStatements.haveStatements()) {
                    StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent,
                            subStatements.statements, iPlusSt + "." + blockIndex, true, newInSyncBlock);
                    analysisBlocks.add(Optional.of(subStatementAnalysis));
                } else {
                    analysisBlocks.add(Optional.empty());
                }
                blockIndex++;
            }
            statementAnalysis.navigationData.blocks.set(ImmutableList.copyOf(analysisBlocks));
            ++statementIndex;
        }
        if (previous != null && setNextAtEnd) {
            previous.navigationData.next.set(Optional.empty());
        }
        return first;
    }

    public boolean atTopLevel() {
        return index.indexOf('.') == 0;
    }

    public void ensure(Message newMessage) {
        if (!messages.contains(newMessage)) {
            messages.add(newMessage);
        }
    }

    /**
     * this method is meant for reading from VariableInfo only, not for writing!
     *
     * @param variableName the name of the variable
     * @return a variable info object
     * @throws IllegalArgumentException if the variable does not exist
     */

    @NotNull
    public VariableInfo getLatestVariableInfo(String variableName) {
        if (!variables.isSet(variableName)) {
            return null; // statements will not have been analysed yet?
        }
        return variables.get(variableName).current();
    }

    public Stream<VariableInfo> streamOfLatestInfoOfVariablesReferringTo(FieldInfo fieldInfo) {
        return variables.stream()
                .map(e -> e.getValue().current())
                .filter(v -> v.variable() instanceof FieldReference fieldReference && fieldReference.fieldInfo == fieldInfo);
    }

    public List<VariableInfo> latestInfoOfVariablesReferringTo(FieldInfo fieldInfo) {
        return streamOfLatestInfoOfVariablesReferringTo(fieldInfo).collect(Collectors.toUnmodifiableList());
    }

    public boolean containsMessage(String messageString) {
        return messages.stream().anyMatch(message -> message.message.contains(messageString) && message.location.equals(location()));
    }

    public interface StateChange extends Function<Expression, Expression> {
        // nothing
    }

    @Override
    public AnnotationMode annotationMode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void transferPropertiesToAnnotations(AnalysisProvider analysisProvider,
                                                E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Location location() {
       return new Location(methodAnalysis.getMethodInfo(), index);
    }

    // ****************************************************************************************

    /**
     * Before iteration 0, all statements: create what was already present higher up
     *
     * @param analyserContext overview object for the analysis of this primary type
     * @param previous        the previous statement, or null if there is none (start of block)
     */
    public void initIteration0(AnalyserContext analyserContext, StatementAnalysis previous) {
        if (previous == null && parent == null) {
            // at the beginning of the method
            if (methodAnalysis.getMethodInfo().hasReturnValue()) {
                Variable retVar = new ReturnVariable(methodAnalysis.getMethodInfo());
                VariableInfoContainer vic = createVariable(analyserContext, retVar, 0, true);
                vic.setStateOnAssignment(VariableInfoContainer.LEVEL_1_INITIALISER, new BooleanConstant(primitives, true));
                READ_FROM_RETURN_VALUE_PROPERTIES.forEach(vp -> vic.setProperty(VariableInfoContainer.LEVEL_1_INITIALISER, vp, vp.falseValue));
            }
            return;
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        copyFrom.variableEntryStream().forEach(e -> copyVariableFromPrevious(e, previous == null ? null :
                previous.index));

        flowData.initialiseAssignmentIds(copyFrom.flowData);
    }

    private void copyVariableFromPrevious(Map.Entry<String, VariableInfoContainer> entry, String indexOfPrevious) {
        String fqn = entry.getKey();
        VariableInfoContainer vic = entry.getValue();
        VariableInfo vi = vic.current();
        VariableInfoContainer newVic;
        // as we move into a loop statement, the index is added to obtain local variable in loop defined outside
        if (!vic.isLocalVariableInLoopDefinedOutside() && statement instanceof LoopStatement) {
            newVic = new VariableInfoContainerImpl(vi.variable(), index, VariableInfoContainer.NOT_A_VARIABLE_FIELD, index,
                    vic.getStatementIndexOfThisLoopVariable());
            vi.propertyStream().forEach(e -> newVic.setProperty(VariableInfoContainer.LEVEL_1_INITIALISER, e.getKey(), e.getValue()));
            // newVic has a new level 1 vi, without a value at this point
        } else if (indexOfPrevious != null && indexOfPrevious.equals(vic.getStatementIndexOfThisLoopVariable())) {
            // this is the very specific situation that the previous statement introduced a loop variable
            // this loop variable should not go beyond the loop statement
            return; // skip
        } else {
            // make a simple reference copy; potentially resetting localVariableInLoopDefinedOutside
            newVic = new VariableInfoContainerImpl(vic, index);
        }
        variables.put(fqn, newVic);
    }

    /**
     * Before iterations 1+, with fieldAnalyses non-empty only potentially for the the first statement
     * of the method.
     *
     * @param analyserContext overview object for the analysis of this primary type
     * @param previous        the previous statement, or null if there is none (start of block)
     */
    public void initIteration1Plus(AnalyserContext analyserContext, MethodInfo currentMethod,
                                   StatementAnalysis previous) {

        // first statement of a method
        if (previous == null && parent == null) {
            for (ParameterInfo parameterInfo : currentMethod.methodInspection.get().getParameters()) {
                VariableInfo prevIteration = findOrNull(parameterInfo, VariableInfoContainer.LEVEL_1_INITIALISER);
                if (prevIteration != null) {
                    VariableInfoContainer vic = findForWriting(analyserContext, parameterInfo, flowData.getInitialTime(), true);
                    ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
                    for (VariableProperty variableProperty : FROM_ANALYSER_TO_PROPERTIES) {
                        int value = parameterAnalysis.getProperty(variableProperty);
                        vic.setProperty(VariableInfoContainer.LEVEL_1_INITIALISER, variableProperty, value);
                    }
                } else {
                    log(ANALYSER, "Skipping parameter {}, not read, assigned", parameterInfo.fullyQualifiedName());
                }
            }
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        int bestLevel = previous == null ? VariableInfoContainer.LEVEL_2_UPDATER :  // parent
                VariableInfoContainer.LEVEL_4_SUMMARY; // previous statement

        // at best level (we can already have Level 4 vi in this statement, still we need to copy into 1
        // Basics_3; on the other hand, READ needs to be at best level
        variables.stream().map(Map.Entry::getValue)
                .filter(vic -> vic.isNotDefinedAtLevel2())
                .forEach(vic -> {
                    VariableInfo viLevel1 = vic.best(VariableInfoContainer.LEVEL_1_INITIALISER);
                    VariableInfo variableInfo = vic.current();
                    boolean haveValueAt3 = vic.getCurrentLevel() == VariableInfoContainer.LEVEL_3_EVALUATION &&
                            variableInfo.getValue() != EmptyExpression.NO_VALUE;
                    int destinationLevel = haveValueAt3 ? VariableInfoContainer.LEVEL_3_EVALUATION :
                            VariableInfoContainer.LEVEL_1_INITIALISER;

                    // for all variables present higher up
                    if (copyFrom != null && copyFrom.variables.isSet(variableInfo.name())) {
                        // it is important that we copy from the same level when copying from the parent! (and not use getLatestVariableInfo)
                        VariableInfoContainer previousVic = copyFrom.variables.get(variableInfo.name());
                        if (previousVic != null) {
                            vic.copy(destinationLevel, previousVic, bestLevel, false, !haveValueAt3);
                        }
                    }
                    // specifically for fields, introduce new data from the field analyser; only at level 1
                    if (variableInfo.variable() instanceof FieldReference fieldReference && destinationLevel == VariableInfoContainer.LEVEL_1_INITIALISER) {

                        if (viLevel1.getStatementTime() == VariableInfoContainer.VARIABLE_FIELD_DELAY) {
                            // see if we can resolve the delay
                            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
                            int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
                            if (effectivelyFinal == Level.TRUE) {
                                vic.setStatementTime(VariableInfoContainer.LEVEL_1_INITIALISER, VariableInfoContainer.NOT_A_VARIABLE_FIELD);
                            } else if (effectivelyFinal == Level.FALSE) {
                                vic.setStatementTime(VariableInfoContainer.LEVEL_1_INITIALISER, flowData.getInitialTime());
                            }
                        }

                        int read = variableInfo.getProperty(READ);
                        if (read >= Level.TRUE && noEarlierAccess(variableInfo.variable(), copyFrom)) {
                            // this is the first statement in the method where this field occurs
                            ExpressionAndLinkedVariables initialValue = initialValueOfField(analyserContext, fieldReference);
                            Map<VariableProperty, Integer> map = propertyMap(analyserContext, fieldReference.fieldInfo);
                            if (!viLevel1.valueIsSet() && !initialValue.expression.isUnknown()) {
                                vic.setInitialValueFromAnalyser(initialValue.expression, new BooleanConstant(primitives, true), map);
                            } else {
                                map.forEach((k, v) -> vic.setProperty(VariableInfoContainer.LEVEL_1_INITIALISER, k, v, false));
                            }
                            if (!viLevel1.linkedVariablesIsSet()) {
                                if (initialValue.linkedVariables != null) {
                                    vic.setLinkedVariables(VariableInfoContainer.LEVEL_1_INITIALISER, initialValue.linkedVariables);
                                }
                            }
                        }
                    }
                });

        if (copyFrom != null) {
            copyFrom.variables.stream()
                    .filter(e -> !copyFrom.index.equals(e.getValue().getStatementIndexOfThisLoopVariable()))
                    .forEach(e -> {
                        String fqn = e.getKey();
                        VariableInfoContainer vicFrom = e.getValue();
                        Variable variable = vicFrom.current().variable();
                        if (!variables.isSet(fqn) && variable instanceof LocalVariableReference) {
                            // other variables that don't exist here and that we do not want to copy: foreign fields,
                            // such as System.out
                            VariableInfoContainer newVic = new VariableInfoContainerImpl(vicFrom, null);
                            variables.put(fqn, newVic);
                        }
                    });
        }
    }

    /**
     * From child blocks into the parent block; determine the value and properties for the current statement
     *
     * @param evaluationContext       for expression evaluations
     * @param lastStatements          the last statement of each of the blocks
     * @param atLeastOneBlockExecuted true if we can (potentially) discard the current value
     * @param statementTime           the statement time of subBlocks
     */
    public void copyBackLocalCopies(EvaluationContext evaluationContext,
                                    List<StatementAnalyser> lastStatements,
                                    boolean atLeastOneBlockExecuted,
                                    int statementTime,
                                    Expression state) {

        // we need to make a synthesis of the variable state of fields, local copies, etc.
        // some blocks are guaranteed to be executed, others are only executed conditionally.
        Stream<Map.Entry<String, VariableInfoContainer>> variableStream = makeVariableStream(lastStatements);
        Set<String> merged = new HashSet<>();
        variableStream.filter(vic -> vic.getValue().isNotDefinedAtLevel2()).forEach(e -> {
            String fqn = e.getKey();
            VariableInfoContainer vic = e.getValue();

            // the variable stream comes from multiple blocks; we ensure that merging takes place once only
            if (merged.add(fqn)) {
                List<VariableInfo> toMerge = lastStatements.stream()
                        .filter(sa -> sa.statementAnalysis.variables.isSet(fqn))
                        .map(sa -> sa.statementAnalysis.variables.get(fqn).current())
                        .collect(Collectors.toList());
                VariableInfoContainer destination;
                if (!variables.isSet(fqn)) {
                    Variable variable = e.getValue().current().variable();
                    destination = createVariable(evaluationContext.getAnalyserContext(), variable, statementTime, false);
                } else {
                    destination = vic;
                }
                destination.merge(VariableInfoContainer.LEVEL_4_SUMMARY, evaluationContext, state, atLeastOneBlockExecuted, toMerge);
            }
        });
    }

    // return a stream of all variables that need merging up
    // note: .distinct() may not work
    private Stream<Map.Entry<String, VariableInfoContainer>> makeVariableStream(List<StatementAnalyser> lastStatements) {
        return Stream.concat(variables.stream(), lastStatements.stream().flatMap(st ->
                st.statementAnalysis.variables.stream().filter(e -> {
                    VariableInfo vi = e.getValue().current();
                    return !vi.variable().isLocal();
                })
        ));
    }


    private static boolean noEarlierAccess(Variable variable, StatementAnalysis previous) {
        if (previous == null || !previous.variables.isSet(variable.fullyQualifiedName())) return true;
        VariableInfo variableInfo = previous.getLatestVariableInfo(variable.fullyQualifiedName());
        if (variableInfo == null) return true;
        return variableInfo.getProperty(ASSIGNED) != Level.TRUE;
    }

    /**
     * This is one of two places which create a {@link VariableInfoContainerImpl} and adds it to the <code>variables</code>
     * map. The other place are the loop local variables in the statement analyser
     *
     * @param analyserContext needed for obtaining properties of analysers, when the variable represents a field or parameter
     * @param variable        the variable
     * @return the container of the new variable
     */
    private VariableInfoContainer createVariable(AnalyserContext analyserContext, Variable variable, int statementTime,
                                                 boolean isNotAssignmentTarget) {
        String fqn = variable.fullyQualifiedName();
        if (variables.isSet(fqn)) throw new UnsupportedOperationException("Already exists");

        int statementTimeForVariable = statementTimeForVariable(analyserContext, variable, statementTime);
        String assignmentIndex = assignmentIndexAtCreation(variable, isNotAssignmentTarget);
        VariableInfoContainer vic = new VariableInfoContainerImpl(variable, assignmentIndex, statementTimeForVariable, null, null);

        variables.put(variable.fullyQualifiedName(), vic);
        log(VARIABLE_PROPERTIES, "Added variable to map: {}", variable.fullyQualifiedName());

        BooleanConstant TRUE = new BooleanConstant(primitives, true);

        // linked variables travel from the parameters via the statements to the fields
        if (variable instanceof ReturnVariable returnVariable) {
            vic.setInitialValueFromAnalyser(new UnknownExpression(returnVariable.returnType, UnknownExpression.RETURN_VALUE), TRUE, Map.of());
            // assignment will be at LEVEL 3
            vic.setLinkedVariablesFromAnalyser(Set.of());
        } else if (variable instanceof This) {
            vic.setInitialValueFromAnalyser(new NewObject(primitives, variable.parameterizedType(), ObjectFlow.NO_FLOW),
                    TRUE, propertyMap(analyserContext, methodAnalysis.getMethodInfo().typeInfo));
            vic.setLinkedVariablesFromAnalyser(Set.of());
        } else if ((variable instanceof ParameterInfo parameterInfo)) {
            ObjectFlow objectFlow = createObjectFlowForNewVariable(analyserContext, variable);
            // TODO copy state from known preconditions
            NewObject instance = new NewObject(primitives, parameterInfo.parameterizedType, objectFlow);
            vic.setInitialValueFromAnalyser(instance, TRUE, propertyMap(analyserContext, parameterInfo));
            vic.setLinkedVariablesFromAnalyser(Set.of());
        } else if (variable instanceof FieldReference fieldReference) {
            ExpressionAndLinkedVariables initialValue = initialValueOfField(analyserContext, fieldReference);
            if (!initialValue.expression.isUnknown()) { // both NO_VALUE and EMPTY_EXPRESSION
                vic.setInitialValueFromAnalyser(initialValue.expression, TRUE, propertyMap(analyserContext, fieldReference.fieldInfo));
            }
            // a field's local copy is always created not modified... can only go "up"
            vic.setProperty(VariableInfoContainer.LEVEL_1_INITIALISER, MODIFIED, 0);
            if (initialValue.linkedVariables != null) {
                vic.setLinkedVariablesFromAnalyser(initialValue.linkedVariables);
            }
        }
        return vic;
    }

    private String assignmentIndexAtCreation(Variable variable, boolean isNotAssignmentTarget) {
        if (isNotAssignmentTarget) {
            if (variable instanceof LocalVariableReference) throw new UnsupportedOperationException();
            return VariableInfoContainer.START_OF_METHOD;
        }
        return index;
    }

    public int statementTimeForVariable(AnalyserContext analyserContext, Variable variable, int statementTime) {
        if (variable instanceof FieldReference fieldReference) {
            int effectivelyFinal = analyserContext.getFieldAnalysis(fieldReference.fieldInfo).getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.DELAY) {
                return VariableInfoContainer.VARIABLE_FIELD_DELAY;
            }
            if (effectivelyFinal == Level.FALSE) {
                return statementTime;
            }
        }
        return VariableInfoContainer.NOT_A_VARIABLE_FIELD;
    }

    private Map<VariableProperty, Integer> propertyMap(AnalyserContext analyserContext, WithInspectionAndAnalysis object) {
        Function<VariableProperty, Integer> f;
        if (object instanceof TypeInfo typeInfo) {
            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(typeInfo);
            f = typeAnalysis::getProperty;
        } else if (object instanceof ParameterInfo parameterInfo) {
            ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
            f = parameterAnalysis::getProperty;
        } else if (object instanceof FieldInfo fieldInfo) {
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldInfo);
            f = fieldAnalysis::getProperty;
        } else throw new UnsupportedOperationException();
        return VariableProperty.FROM_ANALYSER_TO_PROPERTIES.stream()
                .collect(Collectors.toUnmodifiableMap(vp -> vp, f));
    }

    record ExpressionAndLinkedVariables(Expression expression, Set<Variable> linkedVariables) {
    }

    private ExpressionAndLinkedVariables initialValueOfField(AnalyserContext analyserContext, FieldReference fieldReference) {
        boolean inPartOfConstruction = methodAnalysis.getMethodInfo().methodResolution.get().partOfConstruction() ==
                MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
        if (inPartOfConstruction && fieldReference.scope instanceof This thisVariable
                && thisVariable.typeInfo.equals(methodAnalysis.getMethodInfo().typeInfo)) { // field that must be initialised
            Expression initialValue = analyserContext.getFieldAnalysis(fieldReference.fieldInfo).getInitialValue();
            FieldAnalyser fieldAnalyser = analyserContext.getFieldAnalysers().get(fieldReference.fieldInfo);
            if (initialValue.isConstant()) {
                return new ExpressionAndLinkedVariables(initialValue, Set.of());
            }
            EvaluationContext evaluationContext = fieldAnalyser.createEvaluationContext();
            Map<VariableProperty, Integer> properties = evaluationContext.getValueProperties(initialValue);
            return new ExpressionAndLinkedVariables(PropertyWrapper.propertyWrapper(evaluationContext,
                    new NewObject(primitives, fieldReference.parameterizedType(), fieldAnalyser.fieldAnalysis.getObjectFlow()),
                    properties, initialValue.getObjectFlow()), Set.of()); // TODO Set.of() prob not correct
        }
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
        int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effectivelyFinal == Level.DELAY) {
            return new ExpressionAndLinkedVariables(EmptyExpression.NO_VALUE, null);
        }
        boolean variableField = effectivelyFinal == Level.FALSE;
        if (!variableField) {
            Expression efv = fieldAnalysis.getEffectivelyFinalValue();
            if (efv == null) {
                if (analyserContext.getTypeAnalysis(fieldReference.fieldInfo.owner).isBeingAnalysed()) {
                    return new ExpressionAndLinkedVariables(EmptyExpression.NO_VALUE, null);
                }
            } else {
                if (efv.isConstant()) {
                    return new ExpressionAndLinkedVariables(efv, Set.of());
                }
                NewObject newObject;
                if ((newObject = efv.asInstanceOf(NewObject.class)) != null) {
                    return new ExpressionAndLinkedVariables(newObject, fieldAnalysis.getLinkedVariables());
                }
            }
        }
        return new ExpressionAndLinkedVariables(
                new NewObject(primitives, fieldReference.parameterizedType(), fieldAnalysis.getObjectFlow()),
                fieldAnalysis.getLinkedVariables());
    }

    private ObjectFlow createObjectFlowForNewVariable(AnalyserContext analyserContext, Variable variable) {
        if (variable instanceof ParameterInfo parameterInfo) {
            ObjectFlow objectFlow = new ObjectFlow(new Location(parameterInfo),
                    parameterInfo.parameterizedType, Origin.PARAMETER);
            internalObjectFlows.add(objectFlow); // this will be a first
            return objectFlow;
        }

        if (variable instanceof FieldReference fieldReference) {
            ObjectFlow fieldObjectFlow = new ObjectFlow(new Location(fieldReference.fieldInfo),
                    fieldReference.parameterizedType(), Origin.FIELD_ACCESS);
            ObjectFlow objectFlow;
            if (internalObjectFlows.contains(fieldObjectFlow)) {
                objectFlow = internalObjectFlows.stream().filter(of -> of.equals(fieldObjectFlow)).findFirst().orElseThrow();
            } else {
                objectFlow = fieldObjectFlow;
                internalObjectFlows.add(objectFlow);
            }
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
            objectFlow.addPrevious(fieldAnalysis.getObjectFlow());
            return objectFlow;
        }
        return ObjectFlow.NO_FLOW; // will be assigned to soon enough
    }

    public void addProperty(AnalyserContext analyserContext,
                            int level,
                            Variable variable,
                            VariableProperty variableProperty,
                            int value) {
        addProperty(analyserContext, level, statementTime(level), true, variable, variableProperty, value);
    }

    public void addProperty(AnalyserContext analyserContext,
                            int level,
                            int statementTime,
                            boolean isNotAssignmentTarget,
                            Variable variable,
                            VariableProperty variableProperty,
                            int value) {
        Objects.requireNonNull(variable);
        VariableInfoContainer vic = findForWriting(analyserContext, variable, statementTime, isNotAssignmentTarget);
        vic.ensureProperty(level, variableProperty, value);

        Expression currentValue = vic.current().getValue();
        VariableExpression valueWithVariable;
        if ((valueWithVariable = currentValue.asInstanceOf(VariableExpression.class)) == null) return;
        Variable other = valueWithVariable.variable();
        if (!variable.equals(other)) {
            VariableInfoContainer vic2 = findForWriting(analyserContext, other, statementTime, isNotAssignmentTarget);
            vic2.ensureProperty(level, variableProperty, value);

            Expression otherValue = vic2.current().getValue();
            assert !otherValue.isInstanceOf(VariableExpression.class);
        }
    }

    public int statementTime(int level) {
        return level < 3 ? flowData.getInitialTime() : level < 4 ? flowData.getTimeAfterExecution() : flowData.getTimeAfterSubBlocks();
    }

    /**
     * Example: this.j = j; j has a state j<0;
     *
     * @param assignmentTarget this.j
     * @param value            variable value j
     * @return state, translated to assignment target: this.j < 0
     */
    private Expression stateOfValue(Variable assignmentTarget, Expression value, EvaluationContext evaluationContext) {
        VariableExpression valueWithVariable;
        ConditionManager conditionManager = evaluationContext.getConditionManager();
        if ((valueWithVariable = value.asInstanceOf(VariableExpression.class)) != null && conditionManager.haveNonEmptyState() && conditionManager.notInDelayedState()) {
            Expression state = conditionManager.individualStateInfo(evaluationContext, valueWithVariable.variable());
            // now translate the state (j < 0) into state of the assignment target (this.j < 0)
            // TODO for now we're ignoring messages etc. encountered in the re-evaluation
            return state.reEvaluate(evaluationContext, Map.of(value, new VariableExpression(assignmentTarget, ObjectFlow.NO_FLOW))).value();
        }
        return new BooleanConstant(primitives, true);
    }

    public int getProperty(Variable variable, VariableProperty variableProperty) {
        VariableInfo variableInfo = findOrThrow(variable);
        return variableInfo.getProperty(variableProperty);
    }

    /**
     * Find a variable for reading. Intercepts fields and local variables.
     *
     * @param analyserContext because we create the variable if it doesn't exist yet (fields)
     * @param variable        the variable
     * @return the most current variable info object
     */
    public VariableInfo findOrCreateL1(@NotNull AnalyserContext analyserContext,
                                       @NotNull Variable variable,
                                       int statementTime,
                                       boolean isNotAssignmentTarget) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic;
        if (!variables.isSet(fqn)) {
            vic = createVariable(analyserContext, variable, statementTime, isNotAssignmentTarget);
        } else {
            vic = variables.get(fqn);
        }
        VariableInfo vi = vic.best(VariableInfoContainer.LEVEL_2_UPDATER); // including loop assignments
        if (isNotAssignmentTarget) {
            if (vi.variable() instanceof FieldReference fieldReference && vi.getStatementTime() >= 0) {
                return variableInfoOfFieldWhenReading(analyserContext, fieldReference, vi, statementTime);
            }
            if (vic.isLocalVariableInLoopDefinedOutside() && !relevantLocalVariablesAssignedInThisLoopAreFrozen()) {
                return new VariableInfoImpl(variable); // no value, no state
            }
        } // else we need to go to the variable itself
        return vi;
    }

    private boolean relevantLocalVariablesAssignedInThisLoopAreFrozen() {
        StatementAnalysis sa = this;
        while (sa != null) {
            if (sa.statement instanceof LoopStatement) {
                return sa.localVariablesAssignedInThisLoop.isFrozen();
            }
            sa = sa.parent;
        }
        throw new UnsupportedOperationException();
    }

    private VariableInfo variableInfoOfFieldWhenReading(AnalyserContext analyserContext,
                                                        FieldReference fieldReference,
                                                        VariableInfo vi,
                                                        int statementTime) {
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);

        // a variable field can have any value when first read in a method.
        // after statement time goes up, this value may have changed completely
        // therefore we return a new local variable each time we read and statement time has gone up.

        // when there are assignments within the same statement time, however, we stick to the assigned value
        // (we temporarily treat the field as a local variable)
        // so we need to know: have there been assignments AFTER the latest statement time increase?

        String indexOfStatementTime = flowData.assignmentIdOfStatementTime.get(statementTime);
        Expression initialValue;
        String localVariableFqn;
        if (statementTime == vi.getStatementTime() && vi.getAssignmentId().compareTo(indexOfStatementTime) >= 0) {
            // return a local variable with the current field value, numbered as the statement time + assignment ID
            localVariableFqn = fieldReference.fullyQualifiedName() + "$" + statementTime + "$" + vi.getAssignmentId().replace(".", "_");
            initialValue = vi.getValue();
        } else {
            localVariableFqn = fieldReference.fullyQualifiedName() + "$" + statementTime;
            initialValue = new NewObject(primitives, fieldReference.parameterizedType(), fieldAnalysis.getObjectFlow());
        }

        // the statement time of the field indicates the time of the latest assignment
        LocalVariable lv = new LocalVariable(Set.of(LocalVariableModifier.FINAL), localVariableFqn,
                fieldReference.parameterizedType(), List.of(), methodAnalysis.getMethodInfo().typeInfo);
        LocalVariableReference lvr = new LocalVariableReference(analyserContext, lv, List.of());
        VariableInfoContainer lvrVic;
        if (variables.isSet(lvr.fullyQualifiedName())) {
            lvrVic = variables.get(lvr.fullyQualifiedName());
        } else {
            lvrVic = createVariable(analyserContext, lvr, statementTime, false);
            assert initialValue != EmptyExpression.NO_VALUE;
            lvrVic.setInitialValueFromAnalyser(initialValue, new BooleanConstant(primitives, true),
                    propertyMap(analyserContext, fieldReference.fieldInfo));
            lvrVic.setLinkedVariablesFromAnalyser(Set.of(fieldReference)); // linked to the reference

            lvrVic.setProperty(VariableInfoContainer.LEVEL_1_INITIALISER, ASSIGNED, 1);
            lvrVic.setProperty(VariableInfoContainer.LEVEL_1_INITIALISER, READ, 2);
        }
        return lvrVic.current();
    }

    // either go next, or go down
    private StatementAnalysis nextStepTowards(StatementAnalysis sa) {
        for (Optional<StatementAnalysis> maybeDownIntoBlock : sa.navigationData.blocks.get()) {
            if (maybeDownIntoBlock.isPresent() && index.startsWith(maybeDownIntoBlock.get().index)) {
                return maybeDownIntoBlock.get();
            }
        }
        return sa.navigationData.next.get().orElseThrow();
    }

    /**
     * for reading
     *
     * @param variable the variable
     * @return the most current variable info object
     * @throws IllegalArgumentException when the variable does not yet exist
     */
    public VariableInfo findOrThrow(@NotNull Variable variable, int level) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOtherwiseNull(fqn);
        if (vic == null) {
            throw new IllegalArgumentException("Cannot find " + variable + " in " + index);
        }
        return vic.best(level);
    }

    /**
     * for reading
     *
     * @param variable the variable
     * @return the most current variable info object, or null if the variable does not exist
     */
    public VariableInfo findOrNull(@NotNull Variable variable, int level) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOtherwiseNull(fqn);
        if (vic == null) return null;
        return vic.best(level);
    }

    /**
     * for reading
     *
     * @param variable the variable
     * @return the most current variable info object, or null if the variable does not exist
     */
    public VariableInfo findOrThrow(@NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOtherwiseNull(fqn);
        if (vic == null)
            throw new UnsupportedOperationException("Have not yet evaluated " + variable.fullyQualifiedName());
        return vic.current();
    }

    public boolean isLocalVariableAndLocalToThisBlock(String variableName) {
        if (!variables.isSet(variableName)) return false;
        VariableInfoContainer vic = variables.get(variableName);
        if (vic.isLocalVariableInLoopDefinedOutside()) return false;
        VariableInfo variableInfo = vic.current();
        if (!variableInfo.variable().isLocal()) return false;
        if (parent == null) return true;
        return !parent.variables.isSet(variableName);
    }

    /**
     * for writing
     *
     * @param analyserContext because we create the variable if it doesn't exist yet (fields, parameters)
     * @param variable        the variable
     * @return the container from which the setXX methods can be called
     */

    public VariableInfoContainer findForWriting(@NotNull AnalyserContext analyserContext,
                                                @NotNull Variable variable,
                                                int statementTime,
                                                boolean isNotAssignmentTarget) {
        String fqn = variable.fullyQualifiedName();
        if (variables.isSet(fqn)) return variables.get(fqn);
        return createVariable(analyserContext, variable, statementTime, isNotAssignmentTarget);
    }

    /**
     * this method assumes that the variable already exists!
     *
     * @param variableName the variable's fully qualified name
     * @return the container
     */
    public VariableInfoContainer findForWriting(@NotNull String variableName) {
        return variables.get(variableName);
    }

    public VariableInfoContainer findForWriting(@NotNull Variable variable) {
        return variables.get(variable.fullyQualifiedName());
    }

    public int levelAtWhichVariableIsDefined(Variable variable) {
        if (variable instanceof FieldReference) return Integer.MAX_VALUE;
        return internalLevelAtWhichVariableIsDefined(variable.fullyQualifiedName(), 0);
    }

    private int internalLevelAtWhichVariableIsDefined(String variableName, int sum) {
        if (!variables.isSet(variableName)) return sum;
        return parent.internalLevelAtWhichVariableIsDefined(variableName, sum + 1);
    }

    public Set<String> allUnqualifiedVariableNames(InspectionProvider inspectionProvider, TypeInfo currentType) {
        Set<String> fromFields = Resolver.accessibleFieldsStream(inspectionProvider, currentType, currentType.primaryType())
                .map(fieldInfo -> fieldInfo.name).collect(Collectors.toSet());
        Set<String> local = variableStream().map(vi -> vi.variable().simpleName()).collect(Collectors.toSet());
        return SetUtil.immutableUnion(fromFields, local);
    }

    // ***************** OBJECT FLOW CODE ***************

    public ObjectFlow getObjectFlow(Variable variable) {
        VariableInfo aboutVariable = findOrThrow(variable);
        return aboutVariable.getObjectFlow();
    }

    public ObjectFlow addAccess(int level, boolean modifying, Access access, Expression value, EvaluationContext evaluationContext) {
        if (value.getObjectFlow() == ObjectFlow.NO_FLOW) return value.getObjectFlow();
        ObjectFlow potentiallySplit = splitIfNeeded(level, value, evaluationContext);
        if (modifying) {
            log(OBJECT_FLOW, "Set modifying access on {}", potentiallySplit);
            potentiallySplit.setModifyingAccess((MethodAccess) access);
        } else {
            log(OBJECT_FLOW, "Added non-modifying access to {}", potentiallySplit);
            potentiallySplit.addNonModifyingAccess(access);
        }
        return potentiallySplit;
    }

    public ObjectFlow addCallOut(int level, boolean modifying, ObjectFlow callOut, Expression value, EvaluationContext evaluationContext) {
        if (callOut == ObjectFlow.NO_FLOW || value.getObjectFlow() == ObjectFlow.NO_FLOW)
            return value.getObjectFlow();
        ObjectFlow potentiallySplit = splitIfNeeded(level, value, evaluationContext);
        if (modifying) {
            log(OBJECT_FLOW, "Set call-out on {}", potentiallySplit);
            potentiallySplit.setModifyingCallOut(callOut);
        } else {
            log(OBJECT_FLOW, "Added non-modifying call-out to {}", potentiallySplit);
            potentiallySplit.addNonModifyingCallOut(callOut);
        }
        return potentiallySplit;
    }

    private ObjectFlow splitIfNeeded(int level, Expression value, EvaluationContext evaluationContext) {
        ObjectFlow objectFlow = value.getObjectFlow();
        if (objectFlow == ObjectFlow.NO_FLOW) return objectFlow; // not doing anything
        if (objectFlow.haveModifying()) {
            // we'll need to split
            ObjectFlow split = createInternalObjectFlow(objectFlow.type, evaluationContext);
            objectFlow.addNext(split);
            split.addPrevious(objectFlow);
            VariableExpression variableValue;
            if ((variableValue = value.asInstanceOf(VariableExpression.class)) != null) {
                updateObjectFlow(level, variableValue.variable(), split);
            }
            log(OBJECT_FLOW, "Split {}", objectFlow);
            return split;
        }
        return objectFlow;
    }

    private ObjectFlow createInternalObjectFlow(ParameterizedType parameterizedType, EvaluationContext evaluationContext) {
        Location location = evaluationContext.getLocation();
        ObjectFlow objectFlow = new ObjectFlow(location, parameterizedType, Origin.INTERNAL);
        if (!internalObjectFlows.contains(objectFlow)) {
            internalObjectFlows.add(objectFlow);
            log(OBJECT_FLOW, "Created internal flow {}", objectFlow);
            return objectFlow;
        }
        throw new UnsupportedOperationException("Object flow already exists"); // TODO
    }

    private void updateObjectFlow(int level, Variable variable, ObjectFlow objectFlow) {
        VariableInfoContainer variableInfo = findForWriting(variable);
        variableInfo.setObjectFlow(level, objectFlow);
    }

    public Stream<VariableInfo> variableStream() {
        return variables.stream().map(Map.Entry::getValue).map(VariableInfoContainer::current);
    }

    public Stream<Map.Entry<String, VariableInfoContainer>> variableEntryStream() {
        return variables.stream();
    }

    public Stream<VariableInfo> safeVariableStream() {
        return variables.toImmutableMap().values().stream().map(VariableInfoContainer::current);
    }
}
