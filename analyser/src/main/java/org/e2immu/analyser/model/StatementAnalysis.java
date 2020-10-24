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
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.ArrayAccess;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.model.statement.SynchronizedStatement;
import org.e2immu.analyser.model.value.*;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
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
import static org.e2immu.analyser.analyser.VariableProperty.IDENTITY;
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
    public final boolean inPartOfConstruction;

    public final ErrorFlags errorFlags = new ErrorFlags();
    public final NavigationData<StatementAnalysis> navigationData = new NavigationData<>();
    public final SetOnceMap<String, VariableInfo> variables = new SetOnceMap<>();
    public final DependencyGraph<Variable> dependencyGraph = new DependencyGraph<>();
    public final AddOnceSet<ObjectFlow> internalObjectFlows = new AddOnceSet<>();

    public final MethodLevelData methodLevelData = new MethodLevelData();
    public final StateData stateData = new StateData();
    public final FlowData flowData = new FlowData();

    public final SetOnce<Boolean> done = new SetOnce<>(); // if not done, there have been delays

    public StatementAnalysis(Statement statement, StatementAnalysis parent, String index, boolean inSyncBlock, boolean inPartOfConstruction) {
        super(true, index);
        this.index = super.simpleName;
        this.statement = statement;
        this.parent = parent;
        this.inSyncBlock = inSyncBlock;
        this.inPartOfConstruction = inPartOfConstruction;
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

    public boolean inErrorState() {
        boolean parentInErrorState = parent != null && parent.inErrorState();
        if (parentInErrorState) return true;
        return errorFlags.errorValue.isSet() && errorFlags.errorValue.get();
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
        return (statements, startIndex) -> recursivelyCreateAnalysisObjects(parent(), statements, startIndex, false,
                inSyncBlock, inPartOfConstruction);
    }

    public static StatementAnalysis recursivelyCreateAnalysisObjects(
            StatementAnalysis parent,
            List<Statement> statements,
            String indices,
            boolean setNextAtEnd,
            boolean inSyncBlock,
            boolean inPartOfConstruction) {
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
            StatementAnalysis statementAnalysis = new StatementAnalysis(statement, parent, iPlusSt, inSyncBlock, inPartOfConstruction);
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
                StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(parent, statements,
                        iPlusSt + "." + blockIndex, true, newInSyncBlock, inPartOfConstruction);
                analysisBlocks.add(subStatementAnalysis);
                blockIndex++;
            }
            for (Structure subStatements : structure.subStatements) {
                if (subStatements.haveStatements()) {
                    StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(parent, statements,
                            iPlusSt + "." + blockIndex, true, newInSyncBlock, inPartOfConstruction);
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

    public interface StateChange extends Function<Value, Value> {

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
     * Before iteration 0, all statements
     *
     * @param analyserContext    overview object for the analysis of this primary type
     * @param parameterAnalysers the collection of parameter analysers to create local parameter variables for
     * @param previous           the previous statement, or null if there is none (start of block)
     */
    public void initialise(AnalyserContext analyserContext,
                           Collection<ParameterAnalyser> parameterAnalysers,
                           StatementAnalysis previous) {
        if (previous == null && parent == null) {
            for (ParameterAnalyser parameterAnalyser : parameterAnalysers) {
                ensureLocalVariableOrParameter(analyserContext, parameterAnalyser.parameterInfo);
            }
            return;
        }
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
    public void updateStatements(AnalyserContext analyserContext, MethodInfo currentMethod, StatementAnalysis previous) {
        if (previous == null && parent == null) {
            for (ParameterInfo parameterInfo : currentMethod.methodInspection.get().parameters) {
                copyParameterPropertiesFromAnalysis(analyserContext, find(parameterInfo.fullyQualifiedName()), parameterInfo);
            }
        }
        StatementAnalysis copyFrom = previous == null ? parent : previous;
        variableStream().forEach(variableInfo -> {
            if (variableInfo.variable instanceof FieldReference fieldReference) {
                int read = variableInfo.getProperty(READ);
                if (read >= Level.TRUE && noEarlierAccess(fieldReference, copyFrom)) {
                    if (!variableInfo.currentValue.isSet()) {
                        Value initialValue = initialValueOfField(analyserContext, fieldReference);
                        variableInfo.currentValue.set(initialValue); // TODO, what if we're assigning a new value in this statement?, and initialValue is not NO_VALUE?
                        variableInfo.resetValue.set(initialValue);
                        variableInfo.initialValue.set(initialValue);
                    }
                    copyFieldPropertiesFromAnalysis(analyserContext, variableInfo, fieldReference.fieldInfo);
                }
            }
            if (copyFrom != null) {
                VariableInfo previousVariableInfo = copyFrom.variables.get(variableInfo.name);
                if (previousVariableInfo != null) {
                    variableInfo.properties.copyFrom(previousVariableInfo.properties);
                }
            }
        });
    }

    private static boolean noEarlierAccess(FieldReference fieldReference, StatementAnalysis previous) {
        if (previous == null) return true;
        VariableInfo variableInfo = previous.find(fieldReference.fieldInfo.fullyQualifiedName());
        if (variableInfo == null) return true;
        return variableInfo.getProperty(ASSIGNED) != Level.TRUE;
    }

    /**
     * At the end of every iteration, every statement
     * <p>
     * copy current value from previous one if there was no assignment
     */
    public void finalise(StatementAnalysis previous) {
        StatementAnalysis copyValuesFrom;
        if (previous == null) {
            if (parent == null) {
                return;
            }
            copyValuesFrom = parent;
        } else {
            copyValuesFrom = previous;
        }
        variableStream().forEach(variableInfo -> {
            VariableInfo viPrevious = copyValuesFrom.find(variableInfo.name);
            if (viPrevious != null) {
                variableInfo.currentValue.copyIfNotSet(viPrevious.currentValue);
                variableInfo.objectFlow.copyIfNotSet(viPrevious.objectFlow);
                variableInfo.stateOnAssignment.copyIfNotSet(viPrevious.stateOnAssignment);
                variableInfo.resetValue.copyIfNotSet(viPrevious.resetValue);
                variableInfo.initialValue.copyIfNotSet(viPrevious.initialValue);
            }
        });
    }

    public enum FieldReferenceState {
        SINGLE_COPY,
        EFFECTIVELY_FINAL_DELAYED,
        MULTI_COPY
    }

    public void ensureLocalVariableOrParameter(AnalyserContext analyserContext, Variable variable) {
        if (find(variable) != null) return;

        if (variable instanceof LocalVariableReference || variable instanceof ParameterInfo || variable instanceof DependentVariable) {
            ObjectFlow objectFlow = createObjectFlowForNewVariable(analyserContext, variable);
            VariableValue variableValue = new VariableValue(variable, objectFlow);
            VariableInfo vi = internalCreate(analyserContext, variable, variable.fullyQualifiedName(), variableValue, variableValue);
            if (variable instanceof ParameterInfo) {
                vi.currentValue.set(variableValue);
            }
        } else {
            throw new UnsupportedOperationException("Not allowed to add This or FieldReference using this method");
        }
    }

    /**
     * Called every iteration, every occurrence, but only effective in iteration 0, first time
     *
     * @param analyserContext overview object for the analysis of this primary type; needed to grab the field analysers
     * @param fieldReference  reference to the field to be created
     * @return the new or existing VariableInfo object
     */
    public VariableInfo ensureFieldReference(AnalyserContext analyserContext, FieldReference fieldReference) {
        String fqn = fieldReference.fullyQualifiedName();
        VariableInfo vi = find(fqn);
        if (vi != null) return vi;

        Value initialValue = initialValueOfField(analyserContext, fieldReference);
        VariableInfo newVi = internalCreate(analyserContext, fieldReference, fqn, initialValue, initialValue);
        if (initialValue != UnknownValue.NO_VALUE && !newVi.currentValue.isSet()) {
            newVi.currentValue.set(initialValue);
        }
        return newVi;
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
        if (inPartOfConstruction) {
            return ConstantValue.nullValue(fieldReference.fieldInfo.type);
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

    private VariableInfo internalCreate(
            AnalyserContext analyserContext,
            Variable variable,
            String name,
            Value initialValue,
            Value resetValue) {

        VariableInfo variableInfo = new VariableInfo(variable, null, name);
        variables.put(name, variableInfo);
        if (initialValue != UnknownValue.NO_VALUE) variableInfo.initialValue.set(initialValue);
        if (resetValue != UnknownValue.NO_VALUE) variableInfo.resetValue.set(resetValue);

        copyPropertiesFromAnalysis(analyserContext, variable, variableInfo);

        log(VARIABLE_PROPERTIES, "Added variable to map: {}", name);

        // regardless of whether we're a field, a parameter or a local variable...
        if (isRecordType(variable)) {
            TypeInfo recordType = variable.parameterizedType().typeInfo;
            for (FieldInfo recordField : recordType.typeInspection.get().fields) {
                FieldReference fieldReference = new FieldReference(recordField, variable);
                FieldAnalysis recordFieldAnalysis = analyserContext.getFieldAnalysis(recordField);
                Value newInitialValue = computeInitialValue(recordFieldAnalysis);
                boolean variableField = false;// TODO this is not correct
                String newName = fieldReference.fullyQualifiedName();
                Value newResetValue = new VariableValue(fieldReference, newName, ObjectFlow.NO_FLOW, variableField);
                internalCreate(analyserContext, fieldReference, newName, newInitialValue, newResetValue);
            }
        }
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
            variableInfo.setProperty(IMMUTABLE, localVariableReference.concreteReturnType.getProperty(IMMUTABLE));
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
        if (!fieldInfo.hasBeenDefined() || variableInfo.resetValue.isSet() &&
                variableInfo.resetValue.get().isInstanceOf(VariableValue.class)) {
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldInfo);
            for (VariableProperty variableProperty : VariableProperty.FROM_FIELD_TO_PROPERTIES) {
                int value = fieldAnalysis.getProperty(variableProperty);
                if (value == Level.DELAY) value = variableProperty.falseValue;
                variableInfo.setProperty(variableProperty, value);
            }
        }
    }

    private static boolean isRecordType(Variable variable) {
        return !(variable instanceof This) && variable.parameterizedType().typeInfo != null && variable.parameterizedType().typeInfo.isRecord();
    }

    public void addProperty(Variable variable, VariableProperty variableProperty, int value) {

        Objects.requireNonNull(variable);
        VariableInfo aboutVariable = find(variable);
        if (aboutVariable == null) return;
        int current = aboutVariable.getProperty(variableProperty);
        if (current < value) {
            aboutVariable.setProperty(variableProperty, value);
        }

        Value currentValue = aboutVariable.getCurrentValue();
        VariableValue valueWithVariable;
        if ((valueWithVariable = currentValue.asInstanceOf(VariableValue.class)) == null) return;
        Variable other = valueWithVariable.variable;
        if (!variable.equals(other)) {
            addProperty(other, variableProperty, value);
        }
    }

    private static List<String> variableNamesOfLocalRecordVariables(VariableInfo aboutVariable) {
        TypeInfo recordType = aboutVariable.variable.parameterizedType().typeInfo;
        return recordType.typeInspection.getPotentiallyRun().fields.stream()
                .map(fieldInfo -> aboutVariable.name + "." + fieldInfo.name).collect(Collectors.toList());
    }

    // same as addProperty, but "descend" into fields of records as well
    // it is important that "variable" is not used to create VariableValue or so, given that it might be a "superficial" copy

    public void addPropertyAlsoRecords(Variable variable, VariableProperty variableProperty, int value) {
        VariableInfo aboutVariable = find(variable);
        if (aboutVariable == null) return; //not known to us, ignoring!
        recursivelyAddPropertyAlsoRecords(aboutVariable, variableProperty, value);
    }

    private void recursivelyAddPropertyAlsoRecords(VariableInfo aboutVariable, VariableProperty variableProperty, int value) {
        aboutVariable.setProperty(variableProperty, value);
        if (isRecordType(aboutVariable.variable)) {
            for (String name : variableNamesOfLocalRecordVariables(aboutVariable)) {
                VariableInfo aboutLocalVariable = Objects.requireNonNull(find(name));
                recursivelyAddPropertyAlsoRecords(aboutLocalVariable, variableProperty, value);
            }
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
        if ((valueWithVariable = value.asInstanceOf(VariableValue.class)) != null && conditionManager.haveNonEmptyState() && !conditionManager.delayedState()) {
            Value state = conditionManager.individualStateInfo(valueWithVariable.variable);
            // now translate the state (j < 0) into state of the assignment target (this.j < 0)
            // TODO for now we're ignoring messages etc. encountered in the re-evaluation
            return state.reEvaluate(evaluationContext, Map.of(value, new VariableValue(assignmentTarget, ObjectFlow.NO_FLOW))).value;
        }
        return UnknownValue.EMPTY;
    }

    // the difference with resetToUnknownValue is 2-fold: we check properties, and we initialise record fields
    private void resetToNewInstance(VariableInfo aboutVariable, Instance instance, EvaluationContext evaluationContext) {
        // this breaks an infinite NO_VALUE cycle
        if (aboutVariable.resetValue.isSet()) {
            aboutVariable.setCurrentValue(aboutVariable.resetValue.get(),
                    stateOfValue(aboutVariable.variable, aboutVariable.resetValue.get(), evaluationContext),
                    instance.getObjectFlow());
        } else {
            aboutVariable.setCurrentValue(instance, UnknownValue.EMPTY, instance.getObjectFlow());
        }
        // we can only copy the INSTANCE_PROPERTIES like NOT_NULL for VariableValues
        // for other values, NOT_NULL in the properties means a restriction
        if (aboutVariable.getCurrentValue().isInstanceOf(VariableValue.class)) {
            for (VariableProperty variableProperty : INSTANCE_PROPERTIES) {
                aboutVariable.setProperty(variableProperty, instance.getPropertyOutsideContext(variableProperty));
            }
        }
        if (isRecordType(aboutVariable.variable)) {
            List<String> recordNames = variableNamesOfLocalRecordVariables(aboutVariable);
            for (String name : recordNames) {
                VariableInfo aboutLocalVariable = Objects.requireNonNull(find(name));
                resetToInitialValues(aboutLocalVariable, evaluationContext);
            }
        }
    }

    private void resetToInitialValues(VariableInfo aboutVariable, EvaluationContext evaluationContext) {
        Instance instance;
        if (aboutVariable.initialValue.isSet() && (instance = aboutVariable.initialValue.get().asInstanceOf(Instance.class)) != null) {
            resetToNewInstance(aboutVariable, instance, evaluationContext);
        } else {
            aboutVariable.setCurrentValue(aboutVariable.initialValue.get(),
                    stateOfValue(aboutVariable.variable, aboutVariable.initialValue.get(), evaluationContext),
                    aboutVariable.initialValue.get().getObjectFlow());
            if (isRecordType(aboutVariable.variable)) {
                List<String> recordNames = variableNamesOfLocalRecordVariables(aboutVariable);
                for (String name : recordNames) {
                    VariableInfo aboutLocalVariable = Objects.requireNonNull(find(name));
                    resetToInitialValues(aboutLocalVariable, evaluationContext);
                }
            }
        }
    }

    private void resetToUnknownValue(VariableInfo aboutVariable, EvaluationContext evaluationContext) {
        aboutVariable.setCurrentValue(aboutVariable.resetValue.get(),
                stateOfValue(aboutVariable.variable, aboutVariable.resetValue.get(), evaluationContext),
                ObjectFlow.NO_FLOW);
        if (isRecordType(aboutVariable.variable)) {
            List<String> recordNames = variableNamesOfLocalRecordVariables(aboutVariable);
            for (String name : recordNames) {
                VariableInfo aboutLocalVariable = Objects.requireNonNull(find(name));
                resetToUnknownValue(aboutLocalVariable, evaluationContext);
            }
        }
    }

    public int getProperty(Variable variable, VariableProperty variableProperty) {
        VariableInfo aboutVariable = findComplain(variable);
        if (IDENTITY == variableProperty && aboutVariable.variable instanceof ParameterInfo) {
            return ((ParameterInfo) aboutVariable.variable).index == 0 ? Level.TRUE : Level.FALSE;
        }
        return aboutVariable.getProperty(variableProperty);
    }


    public boolean isLocalVariable(VariableInfo variableInfo) {
        if (variableInfo.isLocalVariableReference()) return true;
        if (variableInfo.isLocalCopy() && variableInfo.localCopyOf.isLocalVariableReference())
            return true;
        if (variableInfo.variable instanceof DependentVariable dependentVariable &&
                dependentVariable.arrayName != null) {
            VariableInfo avArray = find(dependentVariable.arrayName);
            return avArray != null && isLocalVariable(avArray);
        }
        return false;
    }


    private FieldReferenceState singleCopy(int effectivelyFinal, boolean inSyncBlock, boolean inPartOfConstruction) {
        if (effectivelyFinal == Level.DELAY) return EFFECTIVELY_FINAL_DELAYED;
        boolean isEffectivelyFinal = effectivelyFinal == Level.TRUE;
        return isEffectivelyFinal || inSyncBlock || inPartOfConstruction ? SINGLE_COPY : MULTI_COPY;
    }


    private Value computeInitialValue(FieldAnalysis recordFieldAnalysis) {
        Value efv = recordFieldAnalysis.getEffectivelyFinalValue();
        if (efv != null) {
            return efv;
        }
        // ? rest should have been done already
        throw new UnsupportedOperationException();
    }

    public boolean isKnown(Variable variable) {
        String name = variable.fullyQualifiedName();
        if (name == null) return false;
        return find(name) != null;
    }

    public DependentVariable ensureArrayVariable(AnalyserContext analyserContext,
                                                 ArrayAccess arrayAccess, String name, Variable arrayVariable) {
        Set<Variable> dependencies = new HashSet<>(arrayAccess.expression.variables());
        dependencies.addAll(arrayAccess.index.variables());
        ParameterizedType parameterizedType = arrayAccess.expression.returnType();
        String arrayName = arrayVariable == null ? null : arrayVariable.fullyQualifiedName();
        DependentVariable dependentVariable = new DependentVariable(parameterizedType, ImmutableSet.copyOf(dependencies), name, arrayName);
        if (!isKnown(dependentVariable)) {
            ensureLocalVariableOrParameter(analyserContext, dependentVariable);
        }
        return dependentVariable;
    }

    public VariableInfo ensureThisVariable(AnalyserContext analyserContext, This thisVariable) {
        String name = thisVariable.fullyQualifiedName();
        VariableInfo vi = find(name);
        if (vi != null) return vi;
        VariableInfo newVi = internalCreate(analyserContext, thisVariable, name, UnknownValue.NO_VALUE, UnknownValue.NO_VALUE);
        newVi.currentValue.set(new VariableValue(thisVariable));
        return newVi;
    }

    private VariableInfo findComplain(@NotNull Variable variable) {
        VariableInfo variableInfo = find(variable);
        if (variableInfo != null) {
            return variableInfo;
        }
        throw new UnsupportedOperationException("Cannot find variable " + variable.fullyQualifiedName());
    }

    public VariableInfo find(@NotNull Variable variable) {
        String fqn = variable.fullyQualifiedName();
        if (fqn == null) return null;
        return find(fqn);
    }

    public VariableInfo find(String name) {
        return variables.getOrDefault(name, null);
    }

    public boolean isDelaysInDependencyGraph() {
        return !dependencyGraph.isFrozen();
    }

    public void removeAllVariables(List<String> toRemove) {
        toRemove.forEach(name -> find(name).remove());
    }

    public int levelVariable(Variable assignmentTarget) {
        VariableInfo variableInfo = find(assignmentTarget);
        if (variableInfo.variable instanceof FieldReference) return Integer.MAX_VALUE;
        int steps = 0;
        while (variableInfo != null) {
            if (variableInfo.isNotLocalCopy()) return steps;
            variableInfo = variableInfo.localCopyOf;
        }
        return -1;
    }

    public void copyBackLocalCopies(List<StatementAnalyser> lastStatements, boolean noBlockMayBeExecuted) {
    }

    public Set<String> allUnqualifiedVariableNames(TypeInfo currentType) {
        Set<String> fromFields = currentType.accessibleFieldsStream().map(fieldInfo -> fieldInfo.name).collect(Collectors.toSet());
        Set<String> local = variableStream().map(vi -> vi.name).collect(Collectors.toSet());
        return SetUtil.immutableUnion(fromFields, local);
    }
    // ***************** ASSIGNMENT BASICS **************


    private VariableInfo ensureLocalCopy(Variable variable) {
        VariableInfo master = findComplain(variable);
        if (!variables.isSet(master.name)) {
            // we'll make a local copy
            VariableInfo copy = master.copy(true);
            variables.put(copy.name, copy);
            return copy;
        }
        return master;
    }

    public ConditionManager assignmentBasics(Variable at, Value value, boolean assignmentToNonEmptyExpression,
                                             EvaluationContext evaluationContext) {
        // assignment to local variable: could be that we're in the block where it was created, then nothing happens
        // but when we're down in some descendant block, a local AboutVariable block is created (we MAY have to undo...)
        VariableInfo aboutVariable = ensureLocalCopy(at);

        if (assignmentToNonEmptyExpression) {
            Instance instance;
            VariableValue variableValue;
            if ((instance = value.asInstanceOf(Instance.class)) != null) {
                resetToNewInstance(aboutVariable, instance, evaluationContext);
            } else if ((variableValue = value.asInstanceOf(VariableValue.class)) != null) {
                VariableInfo other = findComplain(variableValue.variable);
                FieldReferenceState otherFieldReferenceState;
                if (other.variable instanceof FieldReference) {
                    int effectivelyFinal = evaluationContext.getProperty(other.variable, FINAL);
                    otherFieldReferenceState = singleCopy(effectivelyFinal, inSyncBlock, inPartOfConstruction);
                } else {
                    otherFieldReferenceState = SINGLE_COPY;
                }
                if (otherFieldReferenceState == SINGLE_COPY) {
                    aboutVariable.setCurrentValue(value, stateOfValue(at, value, evaluationContext), value.getObjectFlow());
                } else if (otherFieldReferenceState == EFFECTIVELY_FINAL_DELAYED) {
                    aboutVariable.setCurrentValue(UnknownValue.NO_VALUE, UnknownValue.EMPTY, ObjectFlow.NO_FLOW);
                } else {
                    resetToUnknownValue(aboutVariable, evaluationContext);
                }
            } else {
                aboutVariable.setCurrentValue(value, stateOfValue(at, value, evaluationContext), value.getObjectFlow());
            }
            aboutVariable.markAssigned();

            aboutVariable.setProperty(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED,
                    Level.fromBool(guaranteedToBeReached(aboutVariable)));
            return evaluationContext.getConditionManager().variableReassigned(at);
        }
        return evaluationContext.getConditionManager();
    }

    private boolean guaranteedToBeReached(VariableInfo aboutVariable) {
        return true;// TODO
    }

    // ***************** OBJECT FLOW CODE ***************


    public ObjectFlow getObjectFlow(Variable variable) {
        VariableInfo aboutVariable = findComplain(variable);
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
                updateObjectFlow(variableValue.variable, split);
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

    private void updateObjectFlow(Variable variable, ObjectFlow objectFlow) {
        VariableInfo variableInfo = findComplain(variable);
        variableInfo.setObjectFlow(objectFlow);

    }

    public Stream<VariableInfo> variableStream() {
        return variables.stream().map(Map.Entry::getValue).filter(vi -> !vi.properties.isSet(VariableProperty.REMOVED));
    }
}
