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

package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.model.statement.SynchronizedStatement;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.*;
import org.e2immu.annotation.AnnotationMode;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.model.StatementAnalysis.FieldReferenceState.*;
import static org.e2immu.analyser.util.Logger.LogTarget.OBJECT_FLOW;
import static org.e2immu.analyser.util.Logger.LogTarget.VARIABLE_PROPERTIES;
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
    public final DependencyGraph<Variable> dependencyGraph = new DependencyGraph<>();
    public final AddOnceSet<ObjectFlow> internalObjectFlows = new AddOnceSet<>();

    public final MethodLevelData methodLevelData = new MethodLevelData();
    public final StateData stateData = new StateData();
    public final FlowData flowData = new FlowData();

    public final SetOnce<Boolean> done = new SetOnce<>(); // if not done, there have been delays

    public StatementAnalysis(Primitives primitives,
                             MethodAnalysis methodAnalysis,
                             Statement statement, StatementAnalysis parent, String index, boolean inSyncBlock) {
        super(primitives, true, index);
        this.index = super.simpleName;
        this.statement = statement;
        this.parent = parent;
        this.inSyncBlock = inSyncBlock;
        this.methodAnalysis = Objects.requireNonNull(methodAnalysis);
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
                StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent, statements,
                        iPlusSt + "." + blockIndex, true, newInSyncBlock);
                analysisBlocks.add(Optional.of(subStatementAnalysis));
            } else {
                analysisBlocks.add(Optional.empty());
            }
            blockIndex++;
            for (Structure subStatements : structure.subStatements) {
                if (subStatements.haveStatements()) {
                    StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent, statements,
                            iPlusSt + "." + blockIndex, true, newInSyncBlock);
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

    public int stepsUpToLoop() {
        StatementAnalysis sa = this;
        int steps = 0;
        while (sa != null) {
            if (statement instanceof LoopStatement) return steps;
            sa = sa.parent;
        }
        return -1;
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

    public interface StateChange extends Function<Value, Value> {
        // nothing
    }

    @Override
    public AnnotationMode annotationMode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Location location() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isHasBeenDefined() {
        return true;
    }

    // ****************************************************************************************

    /**
     * Before iteration 0, all statements: create what was already present higher up
     *
     * @param analyserContext overview object for the analysis of this primary type
     * @param previous        the previous statement, or null if there is none (start of block)
     */
    public void initialise(AnalyserContext analyserContext, StatementAnalysis previous) {
        if (previous == null && parent == null) {
            // at the beginning of the method
            if (methodAnalysis.getMethodInfo().hasReturnValue()) {
                Variable retVar = new ReturnVariable(methodAnalysis.getMethodInfo());
                VariableInfoContainer vic = createVariable(analyserContext, retVar);
                vic.setStateOnAssignment(VariableInfoContainer.LEVEL_1_INITIALISER, UnknownValue.EMPTY);
                READ_FROM_RETURN_VALUE_PROPERTIES.forEach(vp -> vic.setProperty(VariableInfoContainer.LEVEL_1_INITIALISER, vp, vp.falseValue));
            }
            return;
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        copyFrom.variableStream().forEach(variableInfo -> variables.put(variableInfo.name(),
                new VariableInfoContainerImpl(variableInfo)));
    }

    /**
     * Before iterations 1+, with fieldAnalyses non-empty only potentially for the the first statement
     * of the method.
     * <p>
     * TODO needs rethinking
     *
     * @param analyserContext overview object for the analysis of this primary type
     * @param previous        the previous statement, or null if there is none (start of block)
     */
    public void updateStatements(AnalyserContext analyserContext, MethodInfo currentMethod,
                                 StatementAnalysis previous) {
        if (previous == null && parent == null) {
            for (ParameterInfo parameterInfo : currentMethod.methodInspection.get().parameters) {
                VariableInfoContainer vic = findForWriting(analyserContext, parameterInfo);
                ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
                for (VariableProperty variableProperty : FROM_PARAMETER_TO_PROPERTIES) {
                    int value = parameterAnalysis.getProperty(variableProperty);
                    vic.setProperty(VariableInfoContainer.LEVEL_1_INITIALISER, variableProperty, value);
                }
            }
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        int bestLevel = previous == null ? VariableInfoContainer.LEVEL_1_INITIALISER:  // parent
                VariableInfoContainer.LEVEL_4_SUMMARY; // previous statement

        variableStream().forEach(variableInfo -> {
            VariableInfoContainer vic = findForWriting(variableInfo.name()); // will be present!

            // for all variables present higher up
            if (copyFrom != null && copyFrom.variables.isSet(variableInfo.name())) {
                // it is important that we copy from the same level when copying from the parent! (and not use getLatestVariableInfo)
                VariableInfo previousVariableInfo = copyFrom.variables.get(variableInfo.name()).best(bestLevel);
                if (previousVariableInfo != null) {
                    vic.copy(VariableInfoContainer.LEVEL_1_INITIALISER, previousVariableInfo);
                }
            }
            // specifically for fields
            if (variableInfo.variable() instanceof FieldReference fieldReference) {
                int read = variableInfo.getProperty(READ);
                if (read >= Level.TRUE && noEarlierAccess(variableInfo.variable(), copyFrom)) {
                    // this is the first statement in the method where this field occurs
                    Map<VariableProperty, Integer> map = propertyMap(analyserContext, fieldReference.fieldInfo);
                    if (!variableInfo.valueIsSet()) {
                        Value initialValue = initialValueOfField(analyserContext, fieldReference);
                        vic.setInitialValueFromAnalyser(initialValue, map);
                    } else {
                        map.forEach((k, v) -> vic.setProperty(VariableInfoContainer.LEVEL_1_INITIALISER, k, v));
                    }
                }
            }
        });
    }


    public void copyBackLocalCopies(EvaluationContext evaluationContext,
                                    List<StatementAnalyser> lastStatements,
                                    boolean atLeastOneBlockExecuted,
                                    StatementAnalysis previous) {
        methodLevelData.copyFrom(Stream.concat(previous == null ? Stream.empty() : Stream.of(previous.methodLevelData),
                lastStatements.stream().map(sa -> sa.statementAnalysis.methodLevelData)));

        // we need to make a synthesis of the variable state of fields, local copies, etc.
        // some blocks are guaranteed to be executed, others are only executed conditionally.

        variables.stream().forEach(e -> {
            String fqn = e.getKey();
            VariableInfoContainer vic = e.getValue();

            boolean someChange = lastStatements.stream()
                    .anyMatch(sa -> sa.statementAnalysis.variables.isSet(fqn) && // possibly not set if field, parameter
                            sa.statementAnalysis.variables.get(fqn).getCurrentLevel() > VariableInfoContainer.LEVEL_0_PREVIOUS);
            if (someChange) {
                List<VariableInfo> toMerge = lastStatements.stream()
                        .filter(sa -> sa.statementAnalysis.variables.isSet(fqn))
                        .map(sa -> sa.statementAnalysis.variables.get(fqn).current())
                        .collect(Collectors.toList());
                vic.merge(VariableInfoContainer.LEVEL_4_SUMMARY, evaluationContext, atLeastOneBlockExecuted, toMerge);
            }
        });
    }


    private static boolean noEarlierAccess(Variable variable, StatementAnalysis previous) {
        if (previous == null || !previous.variables.isSet(variable.fullyQualifiedName())) return true;
        VariableInfo variableInfo = previous.getLatestVariableInfo(variable.fullyQualifiedName());
        if (variableInfo == null) return true;
        return variableInfo.getProperty(ASSIGNED) != Level.TRUE;
    }

    public enum FieldReferenceState {
        SINGLE_COPY,
        EFFECTIVELY_FINAL_DELAYED,
        MULTI_COPY
    }

    /**
     * This is (and must remain) the only place which creates a {@link VariableInfoContainerImpl} and adds it to the <code>variables</code>
     * map.
     *
     * @param analyserContext needed for obtaining properties of analysers, when the variable represents a field or parameter
     * @param variable        the variable
     * @return the container of the new variable
     */
    private VariableInfoContainer createVariable(AnalyserContext analyserContext, Variable variable) {
        String fqn = variable.fullyQualifiedName();
        if (variables.isSet(fqn)) throw new UnsupportedOperationException("Already exists");

        VariableInfoContainer vic = new VariableInfoContainerImpl(variable);
        variables.put(variable.fullyQualifiedName(), vic);
        log(VARIABLE_PROPERTIES, "Added variable to map: {}", variable.fullyQualifiedName());

        if (variable instanceof ReturnVariable) {
            vic.setInitialValueFromAnalyser(UnknownValue.RETURN_VALUE, Map.of());
        } else if (variable instanceof This) {
            vic.setInitialValueFromAnalyser(new VariableValue(variable, ObjectFlow.NO_FLOW),
                    propertyMap(analyserContext, methodAnalysis.getMethodInfo().typeInfo));
        } else if ((variable instanceof ParameterInfo parameterInfo)) {
            ObjectFlow objectFlow = createObjectFlowForNewVariable(analyserContext, variable);
            VariableValue variableValue = new VariableValue(variable, objectFlow);
            vic.setInitialValueFromAnalyser(variableValue, propertyMap(analyserContext, parameterInfo));
        } else if (variable instanceof FieldReference fieldReference) {
            Value initialValue = initialValueOfField(analyserContext, fieldReference);
            if (initialValue != UnknownValue.NO_VALUE) {
                vic.setInitialValueFromAnalyser(initialValue, propertyMap(analyserContext, fieldReference.fieldInfo));
            }
        }

        return vic;
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
        return VariableProperty.FROM_PARAMETER_TO_PROPERTIES.stream()
                .collect(Collectors.toUnmodifiableMap(vp -> vp, f));
    }

    /**
     * Properties of fields travel via {@link MethodLevelData}'s <code>fieldSummaries</code> to methods, then to fields.
     * Methods in the construction process are private methods that are ONLY called (transitively) from
     * any of the constructors.
     *
     * <p>
     * Different situations arise for the value of the field set before the first statement:
     *
     * <ol>
     *     <li>
     *         Inside the constructor, or methods part of the construction process:
     *          <ol>
     *              <li>
     *                  Inside a constructor: an initial value is assumed (null for references, zero or false for primitives).
     * <p>
     *                  Value: the relevant null-constant.
     *              </li>
     *              <li>
     *                  After the first assignment (in a constructor, or part of construction process),
     *                  the field acts as a local variable (as if in a sync block for a variable field outside construction).
     *              </li>
     *              <li>
     *                  The initial null value is not appropriate (?) in constructors once modifying methods have been called,
     *                  or in any of the methods in the construction process, before assignment.
     *                  This situation should be forbidden or strongly discouraged.
     * <p>
     *                  TODO: implement a check
     *               </li>
     *          </ol>
     *     </li>
     *     <li>
     *         Outside the construction process:
     *         <ol>
     *             <li>
     *                 In the first iteration, we wait, because we need to determine if the field is effectively final.
     *                 Constant: <code>EFFECTIVELY_FINAL_DELAYED</code>.
     *                 No value set.
     *             </li>
     *             <li>
     *                  Field is effectively final, but <code>effectivelyFinalValue</code> has not yet been set.
     *                  Constant: <code>EFFECTIVELY_FINAL_DELAYED</code>.
     *                  No value set.
     *             </li>
     *             <li>
     *                 Field is effectively final, and an <code>effectivelyFinalValue</code> has been set.
     *                 Constant: <code>EFFECTIVELY_FINAL</code>
     * <p>
     *                 Value: the <code>effectivelyFinalValue</code>.
     *             </li>
     *             <li>
     *                 Field is variable (not effectively final).
     *                 Constant: <code>VARIABLE</code>. Note that the field's value can change during the evaluation of a single
     *                 expression from one evaluation to the next!
     *                 E.g., <code>if(field != null) return field;</code> does NOT guarantee non-null.
     * <p>
     *                 Value: a simple {@link VariableValue} at the start of the first statement of the method.
     *                 Subsequent assignments to the field will potentially yield different values (e.g., a constant, parameter, etc.)
     * <p>
     *                 The field initialiser is taken into account; when absent, the implicit initial null value influences the
     *                 properties such as NOT_NULL.
     *             </li>
     *             <li>
     *                 Inside a synchronized block, a field acts as a local variable.
     *                 Once the first assignment has been made, all properties are computed locally.
     *             </li>
     *         </ol>
     *     </li>
     * </ol>
     *
     * @param analyserContext the context
     * @param fieldReference  the field
     * @return the initial value computed
     */
    private Value initialValueOfField(AnalyserContext analyserContext, FieldReference fieldReference) {
        boolean inPartOfConstruction = methodAnalysis.getMethodInfo().methodResolution.get().partOfConstruction.get() ==
                MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
        if (inPartOfConstruction && fieldReference.scope instanceof This thisVariable
                && thisVariable.typeInfo.equals(methodAnalysis.getMethodInfo().typeInfo)) { // field that must be initialised
            return analyserContext.getFieldAnalysis(fieldReference.fieldInfo).getInitialValue();
        }
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
        int effectivelyFinal = fieldAnalysis.getProperty(FINAL);
        if (effectivelyFinal == Level.DELAY) {
            return UnknownValue.NO_VALUE;
        }
        boolean variableField = effectivelyFinal == Level.FALSE;
        if (!variableField) {
            Value efv = fieldAnalysis.getEffectivelyFinalValue();
            boolean vv = efv instanceof VariableValue;
            if (!vv) {
                if (efv != null) {
                    return efv;
                }
                if (fieldReference.fieldInfo.owner.hasBeenDefined()) {
                    return UnknownValue.NO_VALUE; // delay
                }
            }
        }
        return new VariableValue(fieldReference, fieldReference.fullyQualifiedName(), fieldAnalysis.getObjectFlow(), variableField);
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

    public void addProperty(AnalyserContext analyserContext, int level, Variable variable, VariableProperty variableProperty, int value) {
        Objects.requireNonNull(variable);
        VariableInfoContainer vic = findForWriting(analyserContext, variable);
        vic.setProperty(level, variableProperty, value);

        Value currentValue = vic.current().getValue();
        VariableValue valueWithVariable;
        if ((valueWithVariable = currentValue.asInstanceOf(VariableValue.class)) == null) return;
        Variable other = valueWithVariable.variable;
        if (!variable.equals(other)) {
            addProperty(analyserContext, level, other, variableProperty, value);
        }
    }

    /**
     * Example: this.j = j; j has a state j<0;
     *
     * @param assignmentTarget this.j
     * @param value            variable value j
     * @return state, translated to assignment target: this.j < 0
     */
    private Value stateOfValue(Variable assignmentTarget, Value value, EvaluationContext evaluationContext) {
        VariableValue valueWithVariable;
        ConditionManager conditionManager = evaluationContext.getConditionManager();
        if ((valueWithVariable = value.asInstanceOf(VariableValue.class)) != null && conditionManager.haveNonEmptyState() && conditionManager.notInDelayedState()) {
            Value state = conditionManager.individualStateInfo(evaluationContext, valueWithVariable.variable);
            // now translate the state (j < 0) into state of the assignment target (this.j < 0)
            // TODO for now we're ignoring messages etc. encountered in the re-evaluation
            return state.reEvaluate(evaluationContext, Map.of(value, new VariableValue(assignmentTarget, ObjectFlow.NO_FLOW))).value;
        }
        return UnknownValue.EMPTY;
    }

    public int getProperty(AnalyserContext analyserContext, Variable variable, VariableProperty variableProperty) {
        VariableInfo variableInfo = find(analyserContext, variable);
        return variableInfo.getProperty(variableProperty);
    }

    private FieldReferenceState singleCopy(int effectivelyFinal, boolean inSyncBlock, boolean inPartOfConstruction) {
        if (effectivelyFinal == Level.DELAY) return EFFECTIVELY_FINAL_DELAYED;
        boolean isEffectivelyFinal = effectivelyFinal == Level.TRUE;
        return isEffectivelyFinal || inSyncBlock || inPartOfConstruction ? SINGLE_COPY : MULTI_COPY;
    }

    public void assertVariableExists(Variable variable) {
        assert variables.isSet(variable.fullyQualifiedName());
    }


    /**
     * for reading
     *
     * @param analyserContext because we create the variable if it doesn't exist yet (fields)
     * @param variable        the variable
     * @return the most current variable info object
     */
    public VariableInfo find(@NotNull AnalyserContext analyserContext, @NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic;
        if (!variables.isSet(fqn)) {
            vic = createVariable(analyserContext, variable);
        } else {
            vic = variables.get(fqn);
        }
        return vic.current();
    }

    /**
     * for reading
     *
     * @param variable the variable
     * @return the most current variable info object, or null if the variable does not exist
     */
    public VariableInfo findOrNull(@NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        VariableInfoContainer vic = variables.getOtherwiseNull(fqn);
        if (vic == null) return null;
        return vic.current();
    }

    public boolean isLocalVariableAndLocalToThisBlock(String variableName) {
        if (!variables.isSet(variableName)) return false;
        VariableInfoContainer vic = variables.get(variableName);
        VariableInfo variableInfo = vic.current();
        if (!variableInfo.variable().isLocal()) return false;
        return parent == null || !parent.isLocalVariableAndLocalToThisBlock(variableName);
    }

    /**
     * for writing
     *
     * @param analyserContext because we create the variable if it doesn't exist yet (fields, parameters)
     * @param variable        the variable
     * @return the container from which the setXX methods can be called
     */

    public VariableInfoContainer findForWriting(@NotNull AnalyserContext analyserContext, @NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        if (variables.isSet(fqn)) return variables.get(fqn);
        return createVariable(analyserContext, variable);
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

    public boolean isDelaysInDependencyGraph() {
        return !dependencyGraph.isFrozen();
    }

    public void removeAllVariables(List<String> toRemove) {
        toRemove.forEach(name -> variables.get(name).setProperty(VariableInfoContainer.LEVEL_4_SUMMARY, REMOVED, Level.TRUE));
    }

    public int levelAtWhichVariableIsDefined(Variable variable) {
        if (variable instanceof FieldReference) return Integer.MAX_VALUE;
        return internalLevelAtWhichVariableIsDefined(variable.fullyQualifiedName(), 0);
    }

    private int internalLevelAtWhichVariableIsDefined(String variableName, int sum) {
        if (!variables.isSet(variableName)) return sum;
        return parent.internalLevelAtWhichVariableIsDefined(variableName, sum + 1);
    }

    public Set<String> allUnqualifiedVariableNames(TypeInfo currentType) {
        Set<String> fromFields = currentType.accessibleFieldsStream().map(fieldInfo -> fieldInfo.name).collect(Collectors.toSet());
        Set<String> local = variableStream().map(vi -> vi.variable().simpleName()).collect(Collectors.toSet());
        return SetUtil.immutableUnion(fromFields, local);
    }

    // ***************** OBJECT FLOW CODE ***************

    public ObjectFlow getObjectFlow(AnalyserContext analyserContext, Variable variable) {
        VariableInfo aboutVariable = find(analyserContext, variable);
        return aboutVariable.getObjectFlow();
    }

    public ObjectFlow addAccess(int level, boolean modifying, Access access, Value value, EvaluationContext evaluationContext) {
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

    public ObjectFlow addCallOut(int level, boolean modifying, ObjectFlow callOut, Value value, EvaluationContext evaluationContext) {
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

    private ObjectFlow splitIfNeeded(int level, Value value, EvaluationContext evaluationContext) {
        ObjectFlow objectFlow = value.getObjectFlow();
        if (objectFlow == ObjectFlow.NO_FLOW) return objectFlow; // not doing anything
        if (objectFlow.haveModifying()) {
            // we'll need to split
            ObjectFlow split = createInternalObjectFlow(objectFlow.type, evaluationContext);
            objectFlow.addNext(split);
            split.addPrevious(objectFlow);
            VariableValue variableValue;
            if ((variableValue = value.asInstanceOf(VariableValue.class)) != null) {
                updateObjectFlow(level, variableValue.variable, split);
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
        return variables.stream().map(Map.Entry::getValue)
                .map(VariableInfoContainer::current)
                .filter(vi -> !vi.hasProperty(VariableProperty.REMOVED));
    }
}
