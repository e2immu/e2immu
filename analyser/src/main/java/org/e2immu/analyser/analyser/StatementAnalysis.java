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
import org.e2immu.analyser.model.statement.*;
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
import static org.e2immu.analyser.util.StringUtil.pad;

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
    public final StateData stateData;
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
        stateData = new StateData(statement instanceof LoopStatement);
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

    public List<StatementAnalysis> lastStatementsOfNonEmptySubBlocks() {
        return navigationData.blocks.get().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(sa -> !sa.flowData.isUnreachable())
                .map(StatementAnalysis::lastStatement)
                .collect(Collectors.toList());
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
            String iPlusSt = indices + "." + pad(statementIndex, statements.size());
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
                String indexWithBlock = iPlusSt + "." + pad(blockIndex, structure.subStatements().size() + 1);
                StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent,
                        structure.statements(), indexWithBlock, true, newInSyncBlock);
                analysisBlocks.add(Optional.of(subStatementAnalysis));
            } else {
                analysisBlocks.add(Optional.empty());
            }
            blockIndex++;
            for (Structure subStatements : structure.subStatements()) {
                if (subStatements.haveStatements()) {
                    String indexWithBlock = iPlusSt + "." + pad(blockIndex, structure.subStatements().size() + 1);
                    StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent,
                            subStatements.statements(), indexWithBlock, true, newInSyncBlock);
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

    public Stream<VariableInfo> streamOfLatestInfoOfVariablesReferringTo(FieldInfo fieldInfo, boolean allowLocalCopies) {
        return variables.stream()
                .map(e -> e.getValue().current())
                .filter(v -> v.variable() instanceof FieldReference fieldReference && fieldReference.fieldInfo == fieldInfo ||
                        allowLocalCopies && v.variable() instanceof LocalVariableReference lvr &&
                                lvr.variable.isLocalCopyOf() instanceof FieldReference fr && fr.fieldInfo == fieldInfo
                );
    }

    public List<VariableInfo> latestInfoOfVariablesReferringTo(FieldInfo fieldInfo, boolean allowLocalCopies) {
        return streamOfLatestInfoOfVariablesReferringTo(fieldInfo, allowLocalCopies).collect(Collectors.toUnmodifiableList());
    }

    public boolean containsMessage(String messageString) {
        return messages.stream().anyMatch(message -> message.message.contains(messageString) && message.location.equals(location()));
    }

    public StatementAnalysis navigateTo(String index) {
        if (this.index.equals(index)) return this;
        if (index.startsWith(this.index)) {
            // go into sub-block
            int n = this.index.length();
            int blockIndex = Integer.parseInt(index.substring(n + 1, index.indexOf('.', n + 1)));
            return navigationData.blocks.get().get(blockIndex)
                    .orElseThrow(() -> new UnsupportedOperationException("Looking for " + index + ", block " + blockIndex));
        }
        if (this.index.compareTo(index) < 0 && navigationData.next.get().isPresent()) {
            return navigationData.next.get().get().navigateTo(index);
        }
        throw new UnsupportedOperationException("? have index " + this.index + ", looking for " + index);
    }

    /*
    we can have a simple { } block between the throws statement and the enclosing if
     */

    public StatementAnalysis enclosingConditionalStatement() {
        if (parent == null) throw new UnsupportedOperationException();
        if (parent.statement instanceof IfElseStatement || parent.statement instanceof SwitchStatement ||
                parent.statement instanceof SwitchEntry) {
            return parent;
        }
        return parent.enclosingConditionalStatement();
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
    public void initIteration0(AnalyserContext analyserContext, MethodInfo currentMethod, StatementAnalysis previous) {
        if (previous == null) {
            // at the beginning of every block...
            if (methodAnalysis.getMethodInfo().hasReturnValue()) {
                Variable retVar = new ReturnVariable(methodAnalysis.getMethodInfo());
                VariableInfoContainer vic = createVariable(analyserContext, retVar, 0);
                READ_FROM_RETURN_VALUE_PROPERTIES.forEach(vp -> vic.setProperty(vp, vp.falseValue, VariableInfoContainer.Level.INITIAL));
            }
            // if we're at the beginning of the method, we're done.
            if (parent == null) {
                for (ParameterInfo parameterInfo : currentMethod.methodInspection.get().getParameters()) {
                    createVariable(analyserContext, parameterInfo, 0);
                }
                if (!currentMethod.methodInspection.get().isStatic()) {
                    This thisVariable = new This(analyserContext, currentMethod.typeInfo);
                    createVariable(analyserContext, thisVariable, 0);
                }
                return;
            }
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        copyFrom.variableEntryStream()
                // never copy a return variable from the parent
                .filter(e -> previous != null || !(e.getValue().current().variable() instanceof ReturnVariable))
                .forEach(e -> copyVariableFromPreviousInIteration0(e, previous == null, previous == null ? null : previous.index));

        flowData.initialiseAssignmentIds(copyFrom.flowData);
    }

    private void copyVariableFromPreviousInIteration0(Map.Entry<String, VariableInfoContainer> entry,
                                                      boolean previousIsParent,
                                                      String indexOfPrevious) {
        String fqn = entry.getKey();
        VariableInfoContainer vic = entry.getValue();
        VariableInfo vi = vic.current();
        VariableInfoContainer newVic;
        // as we move into a loop statement, the VariableInLoop is added to obtain local variable in loop defined outside
        if (!vic.isLocalVariableInLoopDefinedOutside() && statement instanceof LoopStatement && vi.variable().isLocal()) {
            newVic = new VariableInfoContainerImpl(vi.variable(),
                    VariableInfoContainer.NOT_A_VARIABLE_FIELD,
                    new VariableInLoop(index, VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE),
                    navigationData.hasSubBlocks());
            vi.propertyStream().forEach(e -> newVic.setProperty(e.getKey(), e.getValue(), VariableInfoContainer.Level.INITIAL));
        } else if (indexOfPrevious != null && indexOfPrevious.equals(vic.getStatementIndexOfThisLoopOrShadowVariable())) {
            // this is the very specific situation that the previous statement introduced a loop variable (or a shadow copy)
            // this loop variable should not go beyond the loop statement
            return; // skip
        } else {
            // make a simple reference copy; potentially resetting localVariableInLoopDefinedOutside
            newVic = new VariableInfoContainerImpl(vic, index, previousIsParent, navigationData.hasSubBlocks());
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

        if (previous == null && parent == null) {
            init1PlusStartOfMethodDoParametersAndThis(analyserContext, currentMethod);
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;

        variables.toImmutableMap().values().forEach(vic -> {
            if (vic.isInitial()) {
                fromFieldAnalyserIntoInitial(analyserContext, vic);
            } else {
                if (vic.hasEvaluation()) vic.copy(); //otherwise, variable not assigned, not read
            }
            ensureLocalCopiesOfConfirmedVariableFields(analyserContext, vic);
        });
        if (copyFrom != null) {
            explicitlyPropagateLocalVariables(copyFrom, previous == null);
        }
    }

    // explicitly copy local variables from above or previous (they cannot be created on demand)
    // loop variables at the statement are not copied to the next one
    private void explicitlyPropagateLocalVariables(StatementAnalysis copyFrom, boolean copyIsParent) {
        copyFrom.variables.stream().filter(e -> copyIsParent ||
                !copyFrom.index.equals(e.getValue().getStatementIndexOfThisLoopOrShadowVariable()))
                .forEach(e -> {
                    String fqn = e.getKey();
                    VariableInfoContainer vicFrom = e.getValue();
                    Variable variable = vicFrom.current().variable();
                    if (!variables.isSet(fqn) && variable instanceof LocalVariableReference) {
                        // other variables that don't exist here and that we do not want to copy: foreign fields,
                        // such as System.out
                        VariableInfoContainer newVic = new VariableInfoContainerImpl(vicFrom,
                                null, copyIsParent, navigationData.hasSubBlocks());
                        variables.put(fqn, newVic);
                    }
                });
    }

    private void init1PlusStartOfMethodDoParametersAndThis(AnalyserContext analyserContext, MethodInfo currentMethod) {
        for (ParameterInfo parameterInfo : currentMethod.methodInspection.get().getParameters()) {
            VariableInfo prevIteration = findOrNull(parameterInfo, VariableInfoContainer.Level.INITIAL);
            if (prevIteration != null) {
                VariableInfoContainer vic = findForWriting(parameterInfo);
                ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
                for (VariableProperty variableProperty : FROM_ANALYSER_TO_PROPERTIES) {
                    int value = parameterAnalysis.getProperty(variableProperty);
                    vic.setProperty(variableProperty, value, VariableInfoContainer.Level.INITIAL);
                }
            } else {
                log(ANALYSER, "Skipping parameter {}, not read, assigned", parameterInfo.fullyQualifiedName());
            }
        }
        // we keep copying in the properties computed on the type into "this"
        if (!currentMethod.methodInspection.get().isStatic()) {
            This thisVariable = new This(analyserContext, currentMethod.typeInfo);
            VariableInfoContainer vic = findForWriting(thisVariable);
            propertyMap(analyserContext, methodAnalysis.getMethodInfo().typeInfo).forEach((vp, v) -> {
                vic.setProperty(vp, v, VariableInfoContainer.Level.INITIAL);
            });
        }
    }

    private void fromFieldAnalyserIntoInitial(AnalyserContext analyserContext, VariableInfoContainer vic) {
        // initial, so we need to copy from analysers.
        // parameters are dealt with in the first part of this method
        // here we deal with copying in values from fields
        VariableInfo variableInfo = vic.current();
        if (!(variableInfo.variable() instanceof FieldReference fieldReference)) return;

        // see if we can resolve a delay in statement time
        if (variableInfo.getStatementTime() == VariableInfoContainer.VARIABLE_FIELD_DELAY) {
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
            int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal != Level.DELAY) {
                vic.setStatementTime(effectivelyFinal == Level.TRUE ?
                        VariableInfoContainer.NOT_A_VARIABLE_FIELD : flowData.getInitialTime());
            }
            // so from here on, isConfirmedVariableField may be set
        }

        // this is the first time we see this field (initial)
        ExpressionAndLinkedVariables initialValue = initialValueOfField(analyserContext, fieldReference);
        Map<VariableProperty, Integer> map = propertyMap(analyserContext, fieldReference.fieldInfo);

        // copy into initial
        VariableInfo viInitial = vic.best(VariableInfoContainer.Level.INITIAL);
        if (!viInitial.valueIsSet() && !initialValue.expression.isUnknown()) {
            vic.setValue(initialValue.expression, map, true);
        } else {
            map.forEach((k, v) -> vic.setProperty(k, v, VariableInfoContainer.Level.INITIAL));
        }
        if (!viInitial.linkedVariablesIsSet() && initialValue.linkedVariables != LinkedVariables.DELAY) {
            vic.setLinkedVariables(initialValue.linkedVariables, true);
        }

        /* copy into evaluation, but only if there is no assignment and no reading

        reading can change the value (e.g. when a modifying method call occurs), but we have a dedicated
        method that reads from INITIAL rather than EVAL so we don't have to copy yet.

        for properties, which are incremental upon reading, we already copy into evaluation,
        because we don't have explicit code available
         */
        VariableInfo viEval = vic.best(VariableInfoContainer.Level.EVALUATION);
        // not assigned in this statement
        if (viEval != viInitial && !vic.isAssignedInThisStatement()) {
            if (!viEval.valueIsSet() && !initialValue.expression.isUnknown() && !viEval.isRead()) {
                vic.setValue(initialValue.expression, map, false);
            } else {
                map.forEach((k, v) -> vic.setProperty(k, v, false, VariableInfoContainer.Level.EVALUATION));
            }
            // if the variable has not been read, it is not present in EVAL, so we set a value
            if (!vic.isReadInThisStatement() && !viEval.linkedVariablesIsSet() && initialValue.linkedVariables != LinkedVariables.DELAY) {
                vic.setLinkedVariables(initialValue.linkedVariables, false);
            }
        }
    }

    private void ensureLocalCopiesOfConfirmedVariableFields(AnalyserContext analyserContext, VariableInfoContainer vic) {
        if (vic.hasEvaluation()) {
            VariableInfo eval = vic.best(VariableInfoContainer.Level.EVALUATION);
            VariableInfo initial = vic.getPreviousOrInitial();
            if (eval.variable() instanceof FieldReference fieldReference &&
                    initial.isConfirmedVariableField() && !eval.getReadAtStatementTimes().isEmpty()) {

                FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
                Primitives primitives = analyserContext.getPrimitives();
                Map<VariableProperty, Integer> propertyMap = VariableProperty.FROM_ANALYSER_TO_PROPERTIES.stream()
                        .collect(Collectors.toUnmodifiableMap(vp -> vp, fieldAnalysis::getProperty));

                for (int statementTime : eval.getReadAtStatementTimes()) {
                    LocalVariableReference localCopy = variableInfoOfFieldWhenReading(analyserContext,
                            fieldReference, initial, statementTime);
                    if (!variables.isSet(localCopy.fullyQualifiedName())) {
                        VariableInfoContainer lvrVic = createVariable(analyserContext, localCopy, statementTime);
                        String indexOfStatementTime = flowData.assignmentIdOfStatementTime.get(statementTime);

                        Expression initialValue = statementTime == initial.getStatementTime() &&
                                initial.getAssignmentId().compareTo(indexOfStatementTime) >= 0 ?
                                initial.getValue() :
                                NewObject.localCopyOfVariableField(primitives, fieldReference.parameterizedType(),
                                        fieldAnalysis.getObjectFlow());
                        assert initialValue != EmptyExpression.NO_VALUE && initialValue != null;
                        lvrVic.setValue(initialValue, propertyMap, true);
                        // we link the local copy to the original, so that modifications on the local copy
                        // imply that there is a (potential) modification on the variable field.
                        // the reverse link is also generated
                        lvrVic.setLinkedVariables(new LinkedVariables(Set.of(fieldReference)), true);
                    }
                }
            }
        }
    }

    public record ConditionAndLastStatement(Expression condition, StatementAnalyser lastStatement) {
    }

    public record ConditionAndVariableInfo(Expression condition, VariableInfo variableInfo) {
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
                                    Expression stateOfConditionManagerBeforeExecution,
                                    List<ConditionAndLastStatement> lastStatements,
                                    boolean atLeastOneBlockExecuted,
                                    int statementTime) {

        // we need to make a synthesis of the variable state of fields, local copies, etc.
        // some blocks are guaranteed to be executed, others are only executed conditionally.

        Stream<Map.Entry<String, VariableInfoContainer>> variableStream = makeVariableStream(lastStatements);
        Set<String> merged = new HashSet<>();
        // explicitly ignore loop and shadow loop variables, they should not exist beyond the statement
        variableStream.filter(vic -> !index.equals(vic.getValue().getStatementIndexOfThisLoopOrShadowVariable())).forEach(e -> {
            String fqn = e.getKey();
            VariableInfoContainer vic = e.getValue();

            // the variable stream comes from multiple blocks; we ensure that merging takes place once only
            // this goes into a list instead of a map, because the condition can be overall NO_VALUE (both if and !if = else)

            if (merged.add(fqn)) {
                List<ConditionAndVariableInfo> toMerge = lastStatements.stream()
                        .filter(e2 -> e2.lastStatement.statementAnalysis.variables.isSet(fqn))
                 //       .filter(e2 -> assignedOrReadInBlock(e2.lastStatement.statementAnalysis.variables.get(fqn).current()))
                        .map(e2 -> new ConditionAndVariableInfo(e2.condition,
                                e2.lastStatement.statementAnalysis.variables.get(fqn).current()))
                        .collect(Collectors.toUnmodifiableList());
                VariableInfoContainer destination;
                if (!variables.isSet(fqn)) {
                    Variable variable = e.getValue().current().variable();
                    destination = createVariable(evaluationContext.getAnalyserContext(), variable, statementTime);
                } else {
                    destination = vic;
                }
                destination.merge(evaluationContext, stateOfConditionManagerBeforeExecution, atLeastOneBlockExecuted, toMerge);
            }
        });
    }

    /*
    we only include a variable in a merge if it is assigned to or read in the block
     */
    private boolean assignedOrReadInBlock(VariableInfo vi) {
        String lowerBound = index + VariableInfoContainer.Level.EVALUATION;
        return vi.getReadId().compareTo(lowerBound) > 0 || vi.getAssignmentId().compareTo(lowerBound) > 0;
    }

    // return a stream of all variables that need merging up
    // note: .distinct() may not work
    private Stream<Map.Entry<String, VariableInfoContainer>> makeVariableStream(List<ConditionAndLastStatement> lastStatements) {
        return Stream.concat(variables.stream(), lastStatements.stream().flatMap(st ->
                st.lastStatement.statementAnalysis.variables.stream().filter(e -> {
                    VariableInfo vi = e.getValue().current();
                    // we don't copy up local variables, unless they're local copies of fields
                    return !vi.variable().isLocal() ||
                            vi.variable() instanceof LocalVariableReference lvr && lvr.variable.isLocalCopyOf() != null;
                })
        ));
    }

    /*
    create a variable, potentially even assign an initial value and a linked variables set.
    everything is written into the INITIAL level, assignmentId and readId are both NOT_YET...
     */
    public VariableInfoContainer createVariable(AnalyserContext analyserContext, Variable variable, int statementTime) {
        String fqn = variable.fullyQualifiedName();
        if (variables.isSet(fqn)) throw new UnsupportedOperationException("Already exists");

        int statementTimeForVariable = statementTimeForVariable(analyserContext, variable, statementTime);

        VariableInfoContainer vic = new VariableInfoContainerImpl(variable, statementTimeForVariable, VariableInLoop.NOT_IN_LOOP, navigationData.hasSubBlocks());

        variables.put(variable.fullyQualifiedName(), vic);
        log(VARIABLE_PROPERTIES, "Added variable to map: {}", variable.fullyQualifiedName());

        // linked variables travel from the parameters via the statements to the fields
        if (variable instanceof ReturnVariable returnVariable) {
            vic.setValue(new UnknownExpression(returnVariable.returnType, UnknownExpression.RETURN_VALUE), Map.of(), true);
            // assignment will be at LEVEL 3
            vic.setLinkedVariables(LinkedVariables.EMPTY, true);

        } else if (variable instanceof This) {
            vic.setValue(NewObject.forCatchOrThis(primitives, variable.parameterizedType()),
                    propertyMap(analyserContext, methodAnalysis.getMethodInfo().typeInfo), true);
            vic.setLinkedVariables(LinkedVariables.EMPTY, true);
            vic.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL, false, VariableInfoContainer.Level.INITIAL);
            vic.setProperty(VariableProperty.METHOD_CALLED, Level.FALSE, false, VariableInfoContainer.Level.INITIAL);

        } else if ((variable instanceof ParameterInfo parameterInfo)) {
            ObjectFlow objectFlow = createObjectFlowForNewVariable(analyserContext, variable);
            // TODO copy state from known preconditions
            Expression state = new BooleanConstant(primitives, true);
            NewObject instance = NewObject.initialValueOfParameter(parameterInfo.parameterizedType, state, objectFlow);
            vic.setValue(instance, propertyMap(analyserContext, parameterInfo), true);
            vic.setLinkedVariables(LinkedVariables.EMPTY, true);

        } else if (variable instanceof FieldReference fieldReference) {
            ExpressionAndLinkedVariables initialValue = initialValueOfField(analyserContext, fieldReference);
            if (!initialValue.expression.isUnknown()) { // both NO_VALUE and EMPTY_EXPRESSION
                vic.setValue(initialValue.expression, propertyMap(analyserContext, fieldReference.fieldInfo), true);
            }
            // a field's local copy is always created not modified... can only go "up"
            vic.setProperty(MODIFIED, 0, VariableInfoContainer.Level.INITIAL);
            if (initialValue.linkedVariables != LinkedVariables.DELAY) {
                vic.setLinkedVariables(initialValue.linkedVariables, true);
            }
        }
        return vic;
    }

    public int statementTimeForVariable(AnalyserContext analyserContext, Variable variable, int statementTime) {
        if (variable instanceof FieldReference fieldReference) {
            boolean inPartOfConstruction = methodAnalysis.getMethodInfo().methodResolution.get().partOfConstruction() ==
                    MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
            if (inPartOfConstruction) return VariableInfoContainer.NOT_A_VARIABLE_FIELD;

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

    record ExpressionAndLinkedVariables(Expression expression, LinkedVariables linkedVariables) {
        ExpressionAndLinkedVariables {
            assert linkedVariables != null;
        }
    }

    private ExpressionAndLinkedVariables initialValueOfField(AnalyserContext analyserContext, FieldReference fieldReference) {
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
        LinkedVariables initialLinkedVariables = LinkedVariables.EMPTY; // rather than fieldAnalysis.getLinkedVariables
        FieldAnalyser fieldAnalyser = analyserContext.getFieldAnalysers().get(fieldReference.fieldInfo);

        boolean inPartOfConstruction = methodAnalysis.getMethodInfo().methodResolution.get().partOfConstruction() ==
                MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
        if (inPartOfConstruction && fieldReference.scope instanceof This thisVariable
                && thisVariable.typeInfo.equals(methodAnalysis.getMethodInfo().typeInfo)) { // field that must be initialised
            Expression initialValue = analyserContext.getFieldAnalysis(fieldReference.fieldInfo).getInitialValue();
            if(initialValue == EmptyExpression.NO_VALUE || initialValue.isConstant()) {
                return new ExpressionAndLinkedVariables(initialValue, LinkedVariables.EMPTY);
            }
            EvaluationContext evaluationContext = fieldAnalyser.createEvaluationContext();
            NewObject newObject = NewObject.initialValueOfFieldPartOfConstruction(evaluationContext, fieldReference, fieldAnalyser.fieldAnalysis.getObjectFlow());
            return new ExpressionAndLinkedVariables(newObject, initialLinkedVariables);
        }

        int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effectivelyFinal == Level.DELAY) {
            return new ExpressionAndLinkedVariables(EmptyExpression.NO_VALUE, LinkedVariables.DELAY);
        }
        boolean variableField = effectivelyFinal == Level.FALSE;
        if (!variableField) {
            Expression efv = fieldAnalysis.getEffectivelyFinalValue();
            if (efv == null) {
                if (analyserContext.getTypeAnalysis(fieldReference.fieldInfo.owner).isBeingAnalysed()) {
                    return new ExpressionAndLinkedVariables(EmptyExpression.NO_VALUE, LinkedVariables.DELAY);
                }
            } else {
                if (efv.isConstant()) {
                    return new ExpressionAndLinkedVariables(efv, LinkedVariables.EMPTY);
                }
                NewObject newObject;
                if ((newObject = efv.asInstanceOf(NewObject.class)) != null) {
                    return new ExpressionAndLinkedVariables(newObject, initialLinkedVariables);
                }
            }
        }
        // variable field, some cases of effectively final field
        NewObject newObject;
        if (fieldAnalyser == null) {
            // not a local field
            int minimalNotNull = analyserContext.getFieldAnalysis(fieldReference.fieldInfo).getProperty(VariableProperty.NOT_NULL);
            newObject = NewObject.initialValueOfExternalField(primitives, fieldReference.parameterizedType(), minimalNotNull, ObjectFlow.NO_FLOW);
        } else {
            newObject = NewObject.initialValueOfField(primitives, fieldReference.parameterizedType(), fieldAnalyser.fieldAnalysis.getObjectFlow());
        }
        return new ExpressionAndLinkedVariables(newObject, initialLinkedVariables);
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

    public int statementTime(VariableInfoContainer.Level level) {
        return switch (level) {
            case INITIAL -> flowData.getInitialTime();
            case EVALUATION -> flowData.getTimeAfterEvaluation();
            case MERGE -> flowData.getTimeAfterSubBlocks();
        };
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
        if ((valueWithVariable = value.asInstanceOf(VariableExpression.class)) != null &&
                //     conditionManager.haveNonEmptyState() &&
                !conditionManager.isDelayed()) {
            Expression state = conditionManager.individualStateInfo(evaluationContext, valueWithVariable.variable());
            // now translate the state (j < 0) into state of the assignment target (this.j < 0)
            // TODO for now we're ignoring messages etc. encountered in the re-evaluation
            return state.reEvaluate(evaluationContext, Map.of(value, new VariableExpression(assignmentTarget, ObjectFlow.NO_FLOW))).value();
        }
        return new BooleanConstant(primitives, true);
    }

    public int getPropertyOfCurrent(Variable variable, VariableProperty variableProperty) {
        VariableInfo variableInfo = findOrThrow(variable);
        return variableInfo.getProperty(variableProperty);
    }

    /**
     * Find a variable for reading. Intercepts variable fields and local variables.
     * This is the general method that must be used by the evaluation context, currentInstance, currentValue
     *
     * @param variable the variable
     * @return the most current variable info object
     */
    public VariableInfo initialValueForReading(@NotNull Variable variable,
                                               int statementTime,
                                               boolean isNotAssignmentTarget) {
        String fqn = variable.fullyQualifiedName();
        if (!variables.isSet(fqn)) {
            return new VariableInfoImpl(variable); // no value, no state; will be created by a MarkRead
        }
        VariableInfoContainer vic = variables.get(fqn);
        VariableInfo vi = vic.getPreviousOrInitial();
        if (isNotAssignmentTarget) {
            if (vi.variable() instanceof FieldReference fieldReference && vi.isConfirmedVariableField()) {
                String localVariableFqn = createLocalFieldCopyFQN(vi, fieldReference, statementTime);
                if (!variables.isSet(localVariableFqn)) {
                    // it is possible that the field has been assigned to, so it exists, but the local copy does not yet
                    return new VariableInfoImpl(variable);
                }
                return variables.get(localVariableFqn).current();
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


    public record FindLoopResult(StatementAnalysis statementAnalysis, int steps) {
    }

    /*
    We've encountered a break or continue statement, and need to find the corresponding loop...
     */
    public FindLoopResult findLoopByLabel(BreakOrContinueStatement breakOrContinue) {
        StatementAnalysis sa = this;
        int cnt = 0;
        while (sa != null) {
            if (sa.statement instanceof LoopStatement loop &&
                    (!breakOrContinue.hasALabel() || loop.label != null && loop.label.equals(breakOrContinue.label))) {
                return new FindLoopResult(sa, cnt);
            }
            sa = sa.parent;
            cnt++;
        }
        throw new UnsupportedOperationException();
    }

    /*
    Two situations for a variable field. First, when there's consecutive reads after an assignment, each in increasing
    statement times, we simply use this statement time in the name: $st.
    Only when statement time hasn't increased, but assignments have, we use the combination $st$assignment id.
     */
    public String createLocalFieldCopyFQN(VariableInfo fieldVi, FieldReference fieldReference, int statementTime) {
        String indexOfStatementTime = flowData.assignmentIdOfStatementTime.get(statementTime);
        String prefix = fieldReference.fullyQualifiedName() + "$" + statementTime;
        if (statementTime == fieldVi.getStatementTime() && fieldVi.getAssignmentId().compareTo(indexOfStatementTime) >= 0) {
            // return a local variable with the current field value, numbered as the statement time + assignment ID
            return prefix + "$" + fieldVi.getAssignmentId().replace(".", "_");
        }
        return prefix;
    }

    public LocalVariableReference variableInfoOfFieldWhenReading(AnalyserContext analyserContext,
                                                                 FieldReference fieldReference,
                                                                 VariableInfo fieldVi,
                                                                 int statementTime) {
        // a variable field can have any value when first read in a method.
        // after statement time goes up, this value may have changed completely
        // therefore we return a new local variable each time we read and statement time has gone up.

        // when there are assignments within the same statement time, however, we stick to the assigned value
        // (we temporarily treat the field as a local variable)
        // so we need to know: have there been assignments AFTER the latest statement time increase?

        String localVariableFqn = createLocalFieldCopyFQN(fieldVi, fieldReference, statementTime);

        // the statement time of the field indicates the time of the latest assignment
        LocalVariable lv = new LocalVariable.Builder()
                .addModifier(LocalVariableModifier.FINAL)
                .setName(localVariableFqn)
                .setParameterizedType(fieldReference.parameterizedType())
                .setIsLocalCopyOf(fieldReference)
                .setOwningType(methodAnalysis.getMethodInfo().typeInfo)
                .build();
        return new LocalVariableReference(analyserContext, lv, List.of());
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
     * helper method
     *
     * @param variable the variable
     * @return the most current variable info object
     * @throws IllegalArgumentException when the variable does not yet exist
     */
    public Expression initialValueOfReturnVariable(@NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOtherwiseNull(fqn);
        if (vic == null) {
            throw new IllegalArgumentException("Cannot find " + variable + " in " + index);
        }
        return vic.getPreviousOrInitial().getValue();
    }

    /**
     * for reading, helper method; not for general use
     *
     * @param variable the variable
     * @return the most current variable info object, or null if the variable does not exist
     */
    public VariableInfo findOrNull(@NotNull Variable variable, VariableInfoContainer.Level level) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOtherwiseNull(fqn);
        if (vic == null) return null;
        return vic.best(level);
    }

    /**
     * for reading, helper method; not for general use
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
     * this method assumes that the variable already exists!
     *
     * @param variableName the variable's fully qualified name
     * @return the container
     */
    public VariableInfoContainer findForWriting(@NotNull String variableName) {
        return variables.get(variableName);
    }

    /*
    will cause errors if variable does not exist yet!
    before you write, you'll have to ensureEvaluation
     */
    public VariableInfoContainer findForWriting(@NotNull Variable variable) {
        return variables.get(variable.fullyQualifiedName());
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

    public ObjectFlow addAccess(boolean modifying, Access access, Expression value, EvaluationContext evaluationContext) {
        if (value.getObjectFlow() == ObjectFlow.NO_FLOW) return value.getObjectFlow();
        ObjectFlow potentiallySplit = splitIfNeeded(value, evaluationContext, false); // TODO check false
        if (modifying) {
            log(OBJECT_FLOW, "Set modifying access on {}", potentiallySplit);
            potentiallySplit.setModifyingAccess((MethodAccess) access);
        } else {
            log(OBJECT_FLOW, "Added non-modifying access to {}", potentiallySplit);
            potentiallySplit.addNonModifyingAccess(access);
        }
        return potentiallySplit;
    }

    public ObjectFlow addCallOut(boolean modifying, ObjectFlow callOut, Expression value, EvaluationContext evaluationContext) {
        if (callOut == ObjectFlow.NO_FLOW || value.getObjectFlow() == ObjectFlow.NO_FLOW)
            return value.getObjectFlow();
        ObjectFlow potentiallySplit = splitIfNeeded(value, evaluationContext, false); // TODO check false
        if (modifying) {
            log(OBJECT_FLOW, "Set call-out on {}", potentiallySplit);
            potentiallySplit.setModifyingCallOut(callOut);
        } else {
            log(OBJECT_FLOW, "Added non-modifying call-out to {}", potentiallySplit);
            potentiallySplit.addNonModifyingCallOut(callOut);
        }
        return potentiallySplit;
    }

    private ObjectFlow splitIfNeeded(Expression value, EvaluationContext evaluationContext, boolean initialOrEvaluation) {
        ObjectFlow objectFlow = value.getObjectFlow();
        if (objectFlow == ObjectFlow.NO_FLOW) return objectFlow; // not doing anything
        if (objectFlow.haveModifying()) {
            // we'll need to split
            ObjectFlow split = createInternalObjectFlow(objectFlow.type, evaluationContext);
            objectFlow.addNext(split);
            split.addPrevious(objectFlow);
            VariableExpression variableValue;
            if ((variableValue = value.asInstanceOf(VariableExpression.class)) != null) {
                updateObjectFlow(variableValue.variable(), split, initialOrEvaluation);
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

    private void updateObjectFlow(Variable variable, ObjectFlow objectFlow, boolean initialOrEvaluation) {
        VariableInfoContainer variableInfo = findForWriting(variable);
        variableInfo.setObjectFlow(objectFlow, initialOrEvaluation);
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
