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
import org.e2immu.analyser.model.value.ConstantValue;
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
    public final SetOnceMap<String, VariableInfo> variables = new SetOnceMap<>();
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
        List<StatementAnalysis> list = navigationData.blocks.get();
        return i >= list.size() ? null : list.get(i);
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
        return followReplacements().navigationData.next.get().map(StatementAnalysis::lastStatement).orElse(this);
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
            List<StatementAnalysis> analysisBlocks = new ArrayList<>();

            boolean newInSyncBlock = inSyncBlock || statement instanceof SynchronizedStatement;
            Structure structure = statement.getStructure();
            if (structure.haveStatements()) {
                StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent, statements,
                        iPlusSt + "." + blockIndex, true, newInSyncBlock);
                analysisBlocks.add(subStatementAnalysis);
                blockIndex++;
            }
            for (Structure subStatements : structure.subStatements) {
                if (subStatements.haveStatements()) {
                    StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(primitives, methodAnalysis, parent, statements,
                            iPlusSt + "." + blockIndex, true, newInSyncBlock);
                    analysisBlocks.add(subStatementAnalysis);
                    blockIndex++;
                }
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

    public void copyBackLocalCopies(List<StatementAnalyser> lastStatements, boolean noBlockMayBeExecuted) {
        // TODO implement
    }

    public void ensure(Message newMessage) {
        messages.add(newMessage);
    }

    public interface StateChange extends Function<Value, Value> {
        // nothing
    }

    public interface StatementAnalysisModification extends Runnable {
        // nothing extra at the moment
    }

    public void apply(StatementAnalysisModification modification) {
        modification.run();
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
     * @param previous the previous statement, or null if there is none (start of block)
     */
    public void initialise(StatementAnalysis previous) {
        if (previous == null && parent == null) return;
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        copyFrom.variableStream().forEach(variableInfo -> {
            variables.put(variableInfo.name, variableInfo.copy(false));
        });
    }

    /**
     * Before iterations 1+, with fieldAnalyses non-empty only potentially for the the first statement
     * of the method.
     *
     * @param analyserContext overview object for the analysis of this primary type
     * @param previous        the previous statement, or null if there is none (start of block)
     */
    public void updateStatements(AnalyserContext analyserContext, MethodInfo currentMethod,
                                 StatementAnalysis previous) {
        if (previous == null && parent == null) {
            for (ParameterInfo parameterInfo : currentMethod.methodInspection.get().parameters) {
                copyParameterPropertiesFromAnalysis(analyserContext, find(analyserContext, parameterInfo), parameterInfo);
            }
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        variableStream().forEach(variableInfo -> {
            if (variableInfo.variable instanceof FieldReference fieldReference) {
                int read = variableInfo.getProperty(READ);
                if (read >= Level.TRUE && noEarlierAccess(variableInfo.variable, copyFrom)) {
                    if (!variableInfo.initialValue.isSet()) {
                        Value initialValue = initialValueOfField(analyserContext, fieldReference);
                        variableInfo.initialValue.set(initialValue);
                    }
                    copyFieldPropertiesFromAnalysis(analyserContext, variableInfo, fieldReference.fieldInfo);
                }
            }
            if (copyFrom != null && copyFrom.variables.isSet(variableInfo.name)) {
                VariableInfo previousVariableInfo = copyFrom.variables.get(variableInfo.name);
                if (previousVariableInfo != null) {
                    variableInfo.properties.copyFrom(previousVariableInfo.properties);
                    if (!variableInfo.initialValue.isSet()) {
                        Value value = previousVariableInfo.valueForNextStatement();
                        if (value != UnknownValue.NO_VALUE) {
                            variableInfo.initialValue.set(value);
                        }
                    }
                }
            }
        });
    }

    private static boolean noEarlierAccess(Variable variable, StatementAnalysis previous) {
        if (previous == null || !previous.variables.isSet(variable.fullyQualifiedName())) return true;
        VariableInfo variableInfo = previous.variables.get(variable.fullyQualifiedName());
        if (variableInfo == null) return true;
        return variableInfo.getProperty(ASSIGNED) != Level.TRUE;
    }

    /**
     * At the end of every iteration, every statement
     * <p>
     * copy current value from previous one if there was no assignment
     */
    public void finalise(StatementAnalysis previous, StatementAnalysis previous1Up) {
        StatementAnalysis copyValuesFrom;
        if (previous == null) {
            if (parent == null) {
                return;
            }
            copyValuesFrom = previous1Up;
        } else {
            copyValuesFrom = previous;
        }
        variableStream().forEach(variableInfo -> {
            VariableInfo viPrevious = copyValuesFrom.variables.get(variableInfo.variable.fullyQualifiedName());
            if (viPrevious != null) {
                variableInfo.objectFlow.copyIfNotSet(viPrevious.objectFlow);
                variableInfo.stateOnAssignment.copyIfNotSet(viPrevious.stateOnAssignment);

                assert variableInfo.initialValue.isSet() || variableInfo.expressionValue.isSet() || variableInfo.endValue.isSet();
            }
        });
    }

    public enum FieldReferenceState {
        SINGLE_COPY,
        EFFECTIVELY_FINAL_DELAYED,
        MULTI_COPY
    }

    private VariableInfo createVariable(AnalyserContext analyserContext, Variable variable) {
        String fqn = variable.fullyQualifiedName();
        VariableInfo existing = variables.getOtherwiseNull(fqn);
        if (existing != null) throw new UnsupportedOperationException();
        VariableInfo vi = internalCreate(analyserContext, variable, fqn);

        if (!vi.initialValue.isSet()) {
            if (variable instanceof This) {
                vi.initialValue.set(new VariableValue(variable, ObjectFlow.NO_FLOW));
            } else if ((variable instanceof ParameterInfo)) {
                ObjectFlow objectFlow = createObjectFlowForNewVariable(analyserContext, variable);
                VariableValue variableValue = new VariableValue(variable, objectFlow);
                vi.initialValue.set(variableValue);
            } else if (variable instanceof FieldReference fieldReference) {
                Value initialValue = initialValueOfField(analyserContext, fieldReference);
                if (initialValue != UnknownValue.NO_VALUE) {
                    vi.initialValue.set(initialValue);
                }
            }
        }
        return vi;
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
                && !thisVariable.writeSuper && !thisVariable.explicitlyWriteType) { // ordinary this, so my field
            return ConstantValue.nullValue(analyserContext.getPrimitives(), fieldReference.fieldInfo.type.typeInfo);
        }
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
        int effectivelyFinal = fieldAnalysis.getProperty(FINAL);
        if (effectivelyFinal == Level.DELAY) {
            return UnknownValue.NO_VALUE;
        }
        if (effectivelyFinal == Level.TRUE) {
            Value efv = fieldAnalysis.getEffectivelyFinalValue();
            if (efv != null) {
                return efv;
            }
            if (fieldReference.fieldInfo.owner.hasBeenDefined()) {
                return UnknownValue.NO_VALUE; // delay
            }
            // foreign field, but we will never know
            return new VariableValue(fieldReference, fieldReference.fieldInfo.fullyQualifiedName(), fieldAnalysis.getObjectFlow(), false);
        }
        return new VariableValue(fieldReference, fieldReference.fieldInfo.fullyQualifiedName(), fieldAnalysis.getObjectFlow(), true);
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

    private VariableInfo internalCreate(AnalyserContext analyserContext, Variable variable, String name) {
        VariableInfo variableInfo = new VariableInfo(variable, null, name);
        variables.put(name, variableInfo);
        log(VARIABLE_PROPERTIES, "Added variable to map: {}", name);

        copyPropertiesFromAnalysis(analyserContext, variable, variableInfo);
        return variableInfo;
    }

    private void copyPropertiesFromAnalysis(AnalyserContext analyserContext, Variable variable, VariableInfo variableInfo) {

        // copy properties from the field into the variable properties
        if (variable instanceof FieldReference) {
            FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
            copyFieldPropertiesFromAnalysis(analyserContext, variableInfo, fieldInfo);
        } else if (variable instanceof ParameterInfo parameterInfo) {
            copyParameterPropertiesFromAnalysis(analyserContext, variableInfo, parameterInfo);

            // the following two are merely initialisation
        } else if (variable instanceof This) {
            variableInfo.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        } else if (variable instanceof LocalVariableReference localVariableReference) {
            variableInfo.setProperty(IMMUTABLE, localVariableReference.concreteReturnType.getProperty(analyserContext, IMMUTABLE));
        } // else: dependentVariable
    }

    private void copyParameterPropertiesFromAnalysis(AnalyserContext analyserContext, VariableInfo variableInfo, ParameterInfo parameterInfo) {
        ParameterAnalysis parameterAnalysis = analyserContext.getParameterAnalysis(parameterInfo);
        int immutable = parameterAnalysis.getProperty(IMMUTABLE);
        variableInfo.setProperty(IMMUTABLE, immutable == MultiLevel.DELAY ? IMMUTABLE.falseValue : immutable);

        int notModified1 = parameterAnalysis.getProperty(NOT_MODIFIED_1);
        variableInfo.setProperty(NOT_MODIFIED_1, notModified1 == Level.DELAY ? NOT_MODIFIED_1.falseValue : notModified1);
    }

    private void copyFieldPropertiesFromAnalysis(AnalyserContext analyserContext, VariableInfo variableInfo, FieldInfo fieldInfo) {
        if (!fieldInfo.hasBeenDefined() || variableInfo.initialValue.isSet() &&
                variableInfo.initialValue.get().isInstanceOf(VariableValue.class)) {
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldInfo);
            for (VariableProperty variableProperty : VariableProperty.FROM_FIELD_TO_PROPERTIES) {
                int value = fieldAnalysis.getProperty(variableProperty);
                if (value == Level.DELAY) value = variableProperty.falseValue;
                variableInfo.setProperty(variableProperty, value);
            }
        }
    }

    public VariableInfo addProperty(AnalyserContext analyserContext, Variable variable, VariableProperty variableProperty, int value) {
        Objects.requireNonNull(variable);
        VariableInfo variableInfo = find(analyserContext, variable);
        int current = variableInfo.getProperty(variableProperty);
        if (current < value) {
            variableInfo.setProperty(variableProperty, value);
        }

        Value currentValue = variableInfo.valueForNextStatement();
        VariableValue valueWithVariable;
        if ((valueWithVariable = currentValue.asInstanceOf(VariableValue.class)) != null) {
            Variable other = valueWithVariable.variable;
            if (!variable.equals(other)) {
                addProperty(analyserContext, other, variableProperty, value);
            }
        }
        return variableInfo;
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
        VariableInfo aboutVariable = find(analyserContext, variable);
        if (IDENTITY == variableProperty && aboutVariable.variable instanceof ParameterInfo) {
            return ((ParameterInfo) aboutVariable.variable).index == 0 ? Level.TRUE : Level.FALSE;
        }
        return aboutVariable.getProperty(variableProperty);
    }

    private FieldReferenceState singleCopy(int effectivelyFinal, boolean inSyncBlock, boolean inPartOfConstruction) {
        if (effectivelyFinal == Level.DELAY) return EFFECTIVELY_FINAL_DELAYED;
        boolean isEffectivelyFinal = effectivelyFinal == Level.TRUE;
        return isEffectivelyFinal || inSyncBlock || inPartOfConstruction ? SINGLE_COPY : MULTI_COPY;
    }

    public void assertVariableExists(Variable variable) {
        assert variables.isSet(variable.fullyQualifiedName());
    }

    public VariableInfo find(@NotNull AnalyserContext analyserContext, @NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        VariableInfo vi = variables.getOtherwiseNull(fqn);
        if (vi != null) return vi;
        return createVariable(analyserContext, variable);
    }

    public boolean isDelaysInDependencyGraph() {
        return !dependencyGraph.isFrozen();
    }

    public void removeAllVariables(List<String> toRemove) {
        toRemove.forEach(name -> variables.get(name).remove());
    }

    public int levelAtWhichVariableIsDefined(AnalyserContext analyserContext, Variable assignmentTarget) {
        VariableInfo variableInfo = find(analyserContext, assignmentTarget);
        if (variableInfo.variable instanceof FieldReference) return Integer.MAX_VALUE;
        int steps = 0;
        while (variableInfo != null) {
            if (variableInfo.isNotLocalCopy()) return steps;
            variableInfo = variableInfo.localCopyOf;
        }
        return -1;
    }

    public Set<String> allUnqualifiedVariableNames(TypeInfo currentType) {
        Set<String> fromFields = currentType.accessibleFieldsStream().map(fieldInfo -> fieldInfo.name).collect(Collectors.toSet());
        Set<String> local = variableStream().map(vi -> vi.name).collect(Collectors.toSet());
        return SetUtil.immutableUnion(fromFields, local);
    }

    // ***************** OBJECT FLOW CODE ***************

    public ObjectFlow getObjectFlow(AnalyserContext analyserContext, Variable variable) {
        VariableInfo aboutVariable = find(analyserContext, variable);
        return aboutVariable.getObjectFlow();
    }

    public ObjectFlow addAccess(boolean modifying, Access access, Value value, EvaluationContext evaluationContext) {
        if (value.getObjectFlow() == ObjectFlow.NO_FLOW) return value.getObjectFlow();
        ObjectFlow potentiallySplit = splitIfNeeded(value, evaluationContext);
        if (modifying) {
            log(OBJECT_FLOW, "Set modifying access on {}", potentiallySplit);
            potentiallySplit.setModifyingAccess((MethodAccess) access);
        } else {
            log(OBJECT_FLOW, "Added non-modifying access to {}", potentiallySplit);
            potentiallySplit.addNonModifyingAccess(access);
        }
        return potentiallySplit;
    }

    public ObjectFlow addCallOut(boolean modifying, ObjectFlow callOut, Value value, EvaluationContext evaluationContext) {
        if (callOut == ObjectFlow.NO_FLOW || value.getObjectFlow() == ObjectFlow.NO_FLOW)
            return value.getObjectFlow();
        ObjectFlow potentiallySplit = splitIfNeeded(value, evaluationContext);
        if (modifying) {
            log(OBJECT_FLOW, "Set call-out on {}", potentiallySplit);
            potentiallySplit.setModifyingCallOut(callOut);
        } else {
            log(OBJECT_FLOW, "Added non-modifying call-out to {}", potentiallySplit);
            potentiallySplit.addNonModifyingCallOut(callOut);
        }
        return potentiallySplit;
    }

    private ObjectFlow splitIfNeeded(Value value, EvaluationContext evaluationContext) {
        ObjectFlow objectFlow = value.getObjectFlow();
        if (objectFlow == ObjectFlow.NO_FLOW) return objectFlow; // not doing anything
        if (objectFlow.haveModifying()) {
            // we'll need to split
            ObjectFlow split = createInternalObjectFlow(objectFlow.type, evaluationContext);
            objectFlow.addNext(split);
            split.addPrevious(objectFlow);
            VariableValue variableValue;
            if ((variableValue = value.asInstanceOf(VariableValue.class)) != null) {
                updateObjectFlow(evaluationContext.getAnalyserContext(), variableValue.variable, split);
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

    private void updateObjectFlow(AnalyserContext analyserContext, Variable variable, ObjectFlow objectFlow) {
        VariableInfo variableInfo = find(analyserContext, variable);
        variableInfo.setObjectFlow(objectFlow);
        // TODO this will crash
    }

    public Stream<VariableInfo> variableStream() {
        return variables.stream().map(Map.Entry::getValue).filter(vi -> !vi.properties.isSet(VariableProperty.REMOVED));
    }
}
