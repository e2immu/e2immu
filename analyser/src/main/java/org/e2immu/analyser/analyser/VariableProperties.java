/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.ArrayAccess;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.value.ConstantValue;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.analyser.util.SMapList;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.Logger.LogTarget.OBJECT_FLOW;
import static org.e2immu.analyser.util.Logger.LogTarget.VARIABLE_PROPERTIES;
import static org.e2immu.analyser.util.Logger.log;

// used in MethodAnalyser

class VariableProperties   {
    private static final Logger LOGGER = LoggerFactory.getLogger(VariableProperties.class);

    // the following two variables can be assigned to as we progress through the statements

    public final ConditionManager conditionManager;
    private boolean guaranteedToBeReachedInCurrentBlock = true;
    private NumberedStatement currentStatement;
    private boolean delaysInDependencyGraph;
    private boolean delayedEvaluation;

    // all the rest is final

    // these 2 will be modified by the statement analyser

    final DependencyGraph<Variable> dependencyGraph;

    // modified by adding errors
    final Messages messages;

    // the rest should be not modified

    final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    final int depth;
    final int iteration;
    final Configuration configuration;
    final PatternMatcher patternMatcher;
    final VariableProperties parent;
    final boolean guaranteedToBeReachedByParentStatement;
    final Runnable uponUsingConditional;
    final MethodInfo currentMethod;
    final FieldInfo currentField;
    final TypeInfo currentType;
    final boolean inSyncBlock;

    // locally modified

    private final Map<String, OldAboutVariable> variableProperties = new HashMap<>(); // at their level, 1x per var
    private final Set<ObjectFlow> internalObjectFlows;
    private final List<Value> preconditions = new ArrayList<>();

    // TEST ONLY, in type analyser, for fields
    public VariableProperties(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                              TypeInfo currentType, int iteration, Configuration configuration, PatternMatcher patternMatcher) {
        this(e2ImmuAnnotationExpressions, currentType, iteration, configuration, patternMatcher, null, null, new HashSet<>());
    }

    // in type analyser, for methods
    public VariableProperties(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                              int iteration, Configuration configuration, PatternMatcher patternMatcher, MethodInfo currentMethod) {
        this(e2ImmuAnnotationExpressions, currentMethod.typeInfo, iteration, configuration, patternMatcher, currentMethod, null, new HashSet<>());
    }

    // in type analyser, for fields
    public VariableProperties(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                              int iteration, Configuration configuration, PatternMatcher patternMatcher, FieldInfo currentField) {
        this(e2ImmuAnnotationExpressions, currentField.owner, iteration, configuration, patternMatcher, null, currentField, new HashSet<>());
    }

    private VariableProperties(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                               TypeInfo currentType,
                               int iteration,
                               Configuration configuration,
                               PatternMatcher patternMatcher,
                               MethodInfo currentMethod,
                               FieldInfo currentField,
                               Set<ObjectFlow> internalObjectFlows) {
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.iteration = iteration;
        this.depth = 0;
        this.configuration = configuration;
        this.parent = null;
        uponUsingConditional = null;
        this.currentMethod = currentMethod;
        Value initialState;
        if (currentMethod != null && currentMethod.methodAnalysis.get().precondition.isSet()) {
            initialState = currentMethod.methodAnalysis.get().precondition.get();
        } else {
            initialState = UnknownValue.EMPTY;
        }
        conditionManager = new ConditionManager(UnknownValue.EMPTY, initialState);
        this.currentField = currentField;
        this.currentType = currentType;
        this.dependencyGraph = new DependencyGraph<>();
        guaranteedToBeReachedByParentStatement = true;
        inSyncBlock = currentMethod != null && currentMethod.isSynchronized();
        this.internalObjectFlows = internalObjectFlows;
        this.messages = new Messages();
        this.patternMatcher = patternMatcher;
    }

    public VariableProperties copyWithCurrentMethod(MethodInfo methodInfo) {
        return new VariableProperties(this, depth, methodInfo, null,
                null, conditionManager.getCondition(), conditionManager.getState(), uponUsingConditional,
                methodInfo.isSynchronized(),
                guaranteedToBeReachedByParentStatement);
    }

    private VariableProperties(VariableProperties parent,
                               int depth,
                               MethodInfo currentMethod,
                               FieldInfo currentField,
                               NumberedStatement currentStatement,
                               Value condition,
                               Value state,
                               Runnable uponUsingConditional,
                               boolean inSyncBlock,
                               boolean guaranteedToBeReachedByParentStatement) {
        this.e2ImmuAnnotationExpressions = parent.e2ImmuAnnotationExpressions;
        this.iteration = parent.iteration;
        this.depth = depth;
        this.configuration = parent.configuration;
        this.parent = parent;
        this.uponUsingConditional = uponUsingConditional;
        this.conditionManager = new ConditionManager(condition, state);
        this.currentMethod = currentMethod;
        this.currentStatement = currentStatement;
        this.currentType = parent.currentType;
        this.currentField = currentField;
        dependencyGraph = parent.dependencyGraph;
        this.inSyncBlock = inSyncBlock;
        this.guaranteedToBeReachedByParentStatement = guaranteedToBeReachedByParentStatement;
        this.internalObjectFlows = parent.internalObjectFlows; // TODO this is wrong; we should be making a child object flow
        this.messages = parent.messages;
        this.patternMatcher = parent.patternMatcher;
    }

    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return e2ImmuAnnotationExpressions;
    }

    @Override
    public void addPrecondition(Value precondition) {
        this.preconditions.add(precondition);
    }

    public Stream<Value> streamPreconditions() {
        return preconditions.stream();
    }


    @Override
    public void linkVariables(Variable from, Set<Variable> to) {
        if (to == null) {
            delaysInDependencyGraph = true;
        } else {
            dependencyGraph.addNode(from, ImmutableList.copyOf(to));
        }
    }

    public boolean isDelaysInDependencyGraph() {
        return delaysInDependencyGraph;
    }

    @Override
    public EvaluationContext childPotentiallyInSyncBlock(Value condition,
                                                         Runnable uponUsingConditional,
                                                         boolean inSyncBlock,
                                                         boolean guaranteedToBeReachedByParentStatement) {
        Value safeCondition = condition == null ? UnknownValue.EMPTY : condition;

        // if we're in a sync block, than the condition must be ignored (it is not a condition, but the variable to be synced on)
        // otherwise, we take a new initial state and condition,
        Value newCondition = inSyncBlock ? conditionManager.getCondition() : conditionManager.combineWithCondition(safeCondition);
        Value newState = inSyncBlock ? conditionManager.getState() : conditionManager.combineWithState(safeCondition);

        return new VariableProperties(this,
                depth + 1,
                currentMethod,
                currentField,
                currentStatement,
                newCondition,
                newState,
                uponUsingConditional,
                inSyncBlock || this.inSyncBlock,
                guaranteedToBeReachedByParentStatement);
    }

    @Override
    public EvaluationContext child(Value condition,
                                   Runnable uponUsingConditional,
                                   boolean guaranteedToBeReachedByParentStatement) {
        Value safeCondition = condition == null ? UnknownValue.EMPTY : condition;
        return new VariableProperties(this,
                depth + 1,
                currentMethod,
                currentField,
                currentStatement,
                conditionManager.combineWithCondition(safeCondition),
                conditionManager.combineWithState(safeCondition),
                uponUsingConditional,
                inSyncBlock,
                guaranteedToBeReachedByParentStatement);
    }


    public Collection<OldAboutVariable> variableProperties() {
        return variableProperties.values();
    }

    private OldAboutVariable findComplain(@NotNull Variable variable) {
        OldAboutVariable aboutVariable = find(variable);
        if (aboutVariable != null) {
            return aboutVariable;
        }
        if (variable instanceof FieldReference) {
            ensureFieldReference((FieldReference) variable);
        } else if (variable instanceof This) {
            ensureThisVariable((This) variable);
        }
        OldAboutVariable aboutVariable2ndAttempt = find(variable);
        if (aboutVariable2ndAttempt != null) {
            return aboutVariable2ndAttempt;
        }
        throw new UnsupportedOperationException("Cannot find variable " + variable.detailedString());
    }

    private OldAboutVariable find(@NotNull Variable variable) {
        String name = variableName(variable);
        if (name == null) return null;
        return find(name);
    }

    private OldAboutVariable find(String name) {
        VariableProperties level = this;
        while (level != null) {
            OldAboutVariable aboutVariable = level.variableProperties.get(name);
            if (aboutVariable != null) return aboutVariable;
            level = level.parent;
        }
        return null;
    }

    public void ensureFieldReference(FieldReference fieldReference) {
        String name = variableName(fieldReference);
        if (find(name) != null) return;
        Value resetValue;
        OldAboutVariable.FieldReferenceState fieldReferenceState = singleCopy(fieldReference);
        if (fieldReferenceState == EFFECTIVELY_FINAL_DELAYED) {
            resetValue = UnknownValue.NO_VALUE; // delay
        } else if (fieldReferenceState == MULTI_COPY) {
            resetValue = new VariableValue(this, fieldReference, name);
        } else {
            FieldAnalysis fieldAnalysis = fieldReference.fieldInfo.fieldAnalysis.get();
            int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.TRUE) {
                if (fieldAnalysis.effectivelyFinalValue.isSet()) {
                    resetValue = safeFinalFieldValue(fieldAnalysis.effectivelyFinalValue.get());
                } else if (fieldReference.fieldInfo.owner.hasBeenDefined()) {
                    resetValue = UnknownValue.NO_VALUE; // delay
                } else {
                    // undefined, will never get a value, but may have decent properties
                    // the properties will be copied from fieldAnalysis into properties in internalCreate
                    resetValue = new VariableValue(this, fieldReference, name);
                }
            } else {
                // local variable situation
                resetValue = new VariableValue(this, fieldReference, name);
            }
        }
        internalCreate(fieldReference, name, resetValue, resetValue, fieldReferenceState);
    }

    private Value safeFinalFieldValue(Value v) {
        FinalFieldValue finalFieldValue;
        return (finalFieldValue = v.asInstanceOf(FinalFieldValue.class)) != null ? finalFieldValue.copy(this) : v;
    }

    public void ensureThisVariable(This thisVariable) {
        String name = variableName(thisVariable);
        if (find(name) != null) return;
        VariableValue resetValue = new VariableValue(this, thisVariable, name);
        internalCreate(thisVariable, name, resetValue, resetValue, SINGLE_COPY);
    }

    private OldAboutVariable.FieldReferenceState singleCopy(FieldReference fieldReference) {
        try {
            int effectivelyFinal = fieldReference.fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.DELAY) return EFFECTIVELY_FINAL_DELAYED;
            boolean isEffectivelyFinal = effectivelyFinal == Level.TRUE;
            boolean inConstructionPhase = currentMethod != null && currentMethod.methodResolution.get().partOfConstruction.get();
            return isEffectivelyFinal || inSyncBlock || inConstructionPhase ? SINGLE_COPY : MULTI_COPY;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception while creating a single copy for field reference: {}, current method {}, current field {}",
                    fieldReference.detailedString(), currentMethod, currentField);
            throw rte;
        }
    }

    @NotNull
    public static String variableName(@NotNull Variable variable) {
        String name;
        if (variable instanceof FieldReference) {
            FieldReference fieldReference = (FieldReference) variable;
            // there are 3 cases: a field during construction phase, an effectively final field of the type we're analysing, and a field of a record
            if (fieldReference.scope == null) {
                name = fieldReference.fieldInfo.fullyQualifiedName();
            } else if (fieldReference.scope instanceof This) {
                name = ((This) fieldReference.scope).typeInfo.simpleName + ".this." + fieldReference.fieldInfo.name;
            } else {
                name = fieldReference.scope.name() + "." + fieldReference.fieldInfo.name;
            }
        } else if (variable instanceof This) {
            This thisVariable = (This) variable;
            name = thisVariable.toString();
        } else {
            // parameter, local variable
            name = variable.name();
        }
        log(VARIABLE_PROPERTIES, "Resolved variable {} to {}", variable.detailedString(), name);
        return name;
    }

    @Override
    public void createLocalVariableOrParameter(@NotNull Variable variable) {
        if (variable instanceof LocalVariableReference || variable instanceof ParameterInfo || variable instanceof DependentVariable) {
            Value resetValue = new VariableValue(this, variable, variable.name());
            internalCreate(variable, variable.name(), resetValue, resetValue, SINGLE_COPY);
        } else {
            throw new UnsupportedOperationException("Not allowed to add This or FieldReference using this method");
        }
    }

    private void internalCreate(Variable variable,
                                String name,
                                Value initialValue,
                                Value resetValue,
                                OldAboutVariable.FieldReferenceState fieldReferenceState) {
        ObjectFlow objectFlow;
        if (variable instanceof ParameterInfo) {
            ParameterInfo parameterInfo = (ParameterInfo) variable;
            objectFlow = new ObjectFlow(new Location(parameterInfo),
                    parameterInfo.parameterizedType, Origin.PARAMETER);
            if (!internalObjectFlows.add(objectFlow))
                throw new UnsupportedOperationException("? should not yet be there: " + objectFlow + " vs " + internalObjectFlows);
        } else if (variable instanceof FieldReference) {
            FieldReference fieldReference = (FieldReference) variable;
            ObjectFlow fieldObjectFlow = new ObjectFlow(new Location(fieldReference.fieldInfo),
                    fieldReference.parameterizedType(), Origin.FIELD_ACCESS);
            if (internalObjectFlows.contains(fieldObjectFlow)) {
                objectFlow = internalObjectFlows.stream().filter(of -> of.equals(fieldObjectFlow)).findFirst().orElseThrow();
            } else {
                objectFlow = fieldObjectFlow;
                internalObjectFlows.add(objectFlow);
            }
            objectFlow.addPrevious(fieldReference.fieldInfo.fieldAnalysis.get().getObjectFlow());
        } else {
            // local variable, field reference, this
            // TODO we should have something for fields?
            objectFlow = ObjectFlow.NO_FLOW; // will be assigned to soon enough
        }

        OldAboutVariable aboutVariable = new OldAboutVariable(variable, Objects.requireNonNull(name), null,
                Objects.requireNonNull(initialValue),
                Objects.requireNonNull(resetValue),
                objectFlow,
                Objects.requireNonNull(fieldReferenceState));

        // copy properties from the field into the variable properties
        if (variable instanceof FieldReference) {
            FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
            if (!fieldInfo.hasBeenDefined() || aboutVariable.resetValue.isInstanceOf(VariableValue.class)) {
                for (VariableProperty variableProperty : VariableProperty.FROM_FIELD_TO_PROPERTIES) {
                    int value = fieldInfo.fieldAnalysis.get().getProperty(variableProperty);
                    if (value == Level.DELAY) value = variableProperty.falseValue;
                    aboutVariable.setProperty(variableProperty, value);
                }
            }
        } else if (variable instanceof ParameterInfo) {
            ParameterAnalysis parameterAnalysis = ((ParameterInfo) variable).parameterAnalysis.get();
            int immutable = parameterAnalysis.getProperty(IMMUTABLE);
            aboutVariable.setProperty(IMMUTABLE, immutable == MultiLevel.DELAY ? IMMUTABLE.falseValue : immutable);

            int notModified1 = parameterAnalysis.getProperty(NOT_MODIFIED_1);
            aboutVariable.setProperty(NOT_MODIFIED_1, notModified1 == Level.DELAY ? NOT_MODIFIED_1.falseValue : notModified1);

        } else if (variable instanceof This) {
            aboutVariable.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        } else if (variable instanceof LocalVariableReference) {
            LocalVariableReference localVariableReference = (LocalVariableReference) variable;
            aboutVariable.setProperty(IMMUTABLE, localVariableReference.concreteReturnType.getProperty(IMMUTABLE));
        } // else: dependentVariable

        // copied over the existing one
        if (variableProperties.put(name, aboutVariable) != null) {
            throw new UnsupportedOperationException("?? Duplicating name " + name);
        }
        log(VARIABLE_PROPERTIES, "Added variable to map: {}", name);

        // regardless of whether we're a field, a parameter or a local variable...
        if (isRecordType(variable)) {
            TypeInfo recordType = variable.parameterizedType().typeInfo;
            for (FieldInfo recordField : recordType.typeInspection.getPotentiallyRun().fields) {
                String newName = name + "." + recordField.name;
                FieldReference fieldReference = new FieldReference(recordField, variable);
                Variable newVariable = new RecordField(fieldReference, newName);
                Expression initialiser = computeInitialiser(recordField);
                Value newInitialValue = computeInitialValue(recordField, initialiser);
                Value newResetValue = new VariableValue(this, newVariable, newName);
                internalCreate(newVariable, newName, newInitialValue, newResetValue, fieldReferenceState);
            }
        }
    }

    private static boolean isRecordType(Variable variable) {
        return !(variable instanceof This) && variable.parameterizedType().typeInfo != null && variable.parameterizedType().typeInfo.isRecord();
    }

    private Value computeInitialValue(FieldInfo recordField, Expression initialiser) {
        if (recordField.fieldAnalysis.get().effectivelyFinalValue.isSet()) {
            return safeFinalFieldValue(recordField.fieldAnalysis.get().effectivelyFinalValue.get());
        }
        if (initialiser instanceof EmptyExpression) {
            return recordField.type.defaultValue();
        }
        return initialiser.evaluate(this, (p1, p2, p3, p4) -> {
        }, ForwardEvaluationInfo.DEFAULT);// completely outside the context, but we should try
    }

    private static Expression computeInitialiser(FieldInfo recordField) {
        FieldInspection recordFieldInspection = recordField.fieldInspection.get();
        if (recordFieldInspection.initialiser.isSet()) {
            return recordFieldInspection.initialiser.get().initialiser;
        }
        return EmptyExpression.EMPTY_EXPRESSION;
    }

    @Override
    public void addProperty(Variable variable, VariableProperty variableProperty, int value) {
        Objects.requireNonNull(variable);
        OldAboutVariable aboutVariable = find(variable);
        if (aboutVariable == null) return;
        int current = aboutVariable.getProperty(variableProperty);
        if (current < value) {
            aboutVariable.setProperty(variableProperty, value);
        }

        Value currentValue = aboutVariable.getCurrentValue();
        ValueWithVariable valueWithVariable;
        if ((valueWithVariable = currentValue.asInstanceOf(ValueWithVariable.class)) == null) return;
        Variable other = valueWithVariable.variable;
        if (!variable.equals(other)) {
            addProperty(other, variableProperty, value);
        }
    }

    private static List<String> variableNamesOfLocalRecordVariables(OldAboutVariable aboutVariable) {
        TypeInfo recordType = aboutVariable.variable.parameterizedType().typeInfo;
        return recordType.typeInspection.getPotentiallyRun().fields.stream()
                .map(fieldInfo -> aboutVariable.name + "." + fieldInfo.name).collect(Collectors.toList());
    }

    // same as addProperty, but "descend" into fields of records as well
    // it is important that "variable" is not used to create VariableValue or so, given that it might be a "superficial" copy

    public void addPropertyAlsoRecords(Variable variable, VariableProperty variableProperty, int value) {
        OldAboutVariable aboutVariable = find(variable);
        if (aboutVariable == null) return; //not known to us, ignoring!
        recursivelyAddPropertyAlsoRecords(aboutVariable, variableProperty, value);
    }

    private void recursivelyAddPropertyAlsoRecords(OldAboutVariable aboutVariable, VariableProperty variableProperty, int value) {
        aboutVariable.setProperty(variableProperty, value);
        if (isRecordType(aboutVariable.variable)) {
            for (String name : variableNamesOfLocalRecordVariables(aboutVariable)) {
                OldAboutVariable aboutLocalVariable = Objects.requireNonNull(find(name));
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
    private Value stateOfValue(Variable assignmentTarget, Value value) {
        ValueWithVariable valueWithVariable;
        if ((valueWithVariable = value.asInstanceOf(ValueWithVariable.class)) != null && conditionManager.haveNonEmptyState() && !delayedState()) {
            Value state = conditionManager.individualStateInfo(valueWithVariable.variable);
            // now translate the state (j < 0) into state of the assignment target (this.j < 0)
            return state.reEvaluate(this,
                    Map.of(value, new VariableValue(null, assignmentTarget, assignmentTarget.name())));
        }
        return UnknownValue.EMPTY;
    }

    // the difference with resetToUnknownValue is 2-fold: we check properties, and we initialise record fields
    private void resetToNewInstance(OldAboutVariable aboutVariable, Instance instance) {
        // this breaks an infinite NO_VALUE cycle
        if (aboutVariable.resetValue != UnknownValue.NO_VALUE) {
            aboutVariable.setCurrentValue(aboutVariable.resetValue,
                    stateOfValue(aboutVariable.variable, aboutVariable.resetValue),
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
                OldAboutVariable aboutLocalVariable = Objects.requireNonNull(find(name));
                resetToInitialValues(aboutLocalVariable);
            }
        }
    }

    private void resetToInitialValues(OldAboutVariable aboutVariable) {
        Instance instance;
        if ((instance = aboutVariable.initialValue.asInstanceOf(Instance.class)) != null) {
            resetToNewInstance(aboutVariable, instance);
        } else {
            aboutVariable.setCurrentValue(aboutVariable.initialValue,
                    stateOfValue(aboutVariable.variable, aboutVariable.initialValue),
                    aboutVariable.initialValue.getObjectFlow());
            if (isRecordType(aboutVariable.variable)) {
                List<String> recordNames = variableNamesOfLocalRecordVariables(aboutVariable);
                for (String name : recordNames) {
                    OldAboutVariable aboutLocalVariable = Objects.requireNonNull(find(name));
                    resetToInitialValues(aboutLocalVariable);
                }
            }
        }
    }

    private void resetToUnknownValue(OldAboutVariable aboutVariable) {
        aboutVariable.setCurrentValue(aboutVariable.resetValue,
                stateOfValue(aboutVariable.variable, aboutVariable.resetValue),
                ObjectFlow.NO_FLOW);
        if (isRecordType(aboutVariable.variable)) {
            List<String> recordNames = variableNamesOfLocalRecordVariables(aboutVariable);
            for (String name : recordNames) {
                OldAboutVariable aboutLocalVariable = Objects.requireNonNull(find(name));
                resetToUnknownValue(aboutLocalVariable);
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (parent == null) sb.append("@Root: ");
        else sb.append(parent.toString()).append("; ");
        sb.append(variableProperties.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", ")));
        return sb.toString();
    }

    @Override
    @NotNull
    public Value currentValue(Variable variable) {
        OldAboutVariable aboutVariable = findComplain(variable);
        if (aboutVariable.getProperty(ASSIGNED_IN_LOOP) == Level.TRUE) {
            return aboutVariable.resetValue;
        }
        return aboutVariable.getCurrentValue();
    }

    @Override
    public VariableValue newVariableValue(Variable variable) {
        if (variable instanceof This) throw new UnsupportedOperationException();
        if (variable instanceof FieldReference) {
            ensureFieldReference((FieldReference) variable);
        }
        OldAboutVariable aboutVariable = findComplain(variable);
        // TODO ObjectFlow
        return new VariableValue(this, variable, aboutVariable.name);
    }

    public void setGuaranteedToBeReachedInCurrentBlock(boolean guaranteedToBeReachedInCurrentBlock) {
        this.guaranteedToBeReachedInCurrentBlock = guaranteedToBeReachedInCurrentBlock;
    }

    /**
     * in copyBackLocalCopies we are concerned with
     */
    @Override
    public void merge(EvaluationContext child) {
        copyBackLocalCopies(List.of((VariableProperties) child), false);
    }

    private static final VariableProperty[] BEST = {VariableProperty.SIZE, MODIFIED};

    private static final VariableProperty[] INCREMENT_LEVEL = {READ, ASSIGNED};

    // first we only keep those that have been assigned at the lower level
    // then we get rid of those that are local variables created at the lower level; all the rest stays
    private static final Predicate<OldAboutVariable> ASSIGNED_NOT_LOCAL_VAR = aboutVariable ->
            MultiLevel.value(aboutVariable.getProperty(ASSIGNED), 0) == Level.TRUE &&
                    (!aboutVariable.isLocalVariable() || aboutVariable.isLocalCopy());

    private static Value.FilterResult localVariableFilter(Value value) {
        Set<Variable> variables = value.variables();
        if (variables.size() == 1) {
            Variable variable = variables.stream().findAny().orElseThrow();
            if (variable instanceof LocalVariableReference) {
                return new Value.FilterResult(Map.of(variable, value), UnknownValue.EMPTY);
            }
        }
        return new Value.FilterResult(Map.of(), value);
    }

    /**
     * So we have a number of sub-contexts all at the same level, some guaranteed to be executed,
     * some not. Assignments to variables of the higher level in the sub-level, have caused a local copy to be created.
     * Assignments to fields which have not been seen yet at the higher level, cause originals at the lower levels.
     *
     * @param evaluationContextsGathered the list of contexts gathered
     * @param noBlockMayBeExecuted       true is the default value for many loops, if without else, switch without default, ...
     */
    void copyBackLocalCopies(List<VariableProperties> evaluationContextsGathered, boolean noBlockMayBeExecuted) {
        Map<String, List<VariableProperties>> contextsPerVariable = SMapList.create();
        evaluationContextsGathered
                .forEach(vp -> vp.variableProperties.entrySet().stream()
                        .filter(e -> ASSIGNED_NOT_LOCAL_VAR.test(e.getValue()))
                        .forEach(e -> SMapList.addWithArrayList(contextsPerVariable, e.getKey(), vp)));
        log(VARIABLE_PROPERTIES, "Copying back assignment and properties of {}", contextsPerVariable.keySet());

        // move preconditions upward
        evaluationContextsGathered.forEach(vp -> preconditions.addAll(vp.preconditions));

        // copy the state (but not the condition back up); for now, only if guaranteed to be executed
        if (!noBlockMayBeExecuted) {
            List<Value> states = evaluationContextsGathered.stream()
                    .filter(ec -> ec.guaranteedToBeReachedByParentStatement) // only if the block will be executed!
                    .map(ec -> ec.conditionManager.getState())
                    .filter(v -> v != UnknownValue.EMPTY)
                    .collect(Collectors.toList());
            log(VARIABLE_PROPERTIES, "Have states: {}", states);
            boolean haveDelay = states.stream().anyMatch(v -> v == UnknownValue.NO_VALUE);
            if (haveDelay) {
                conditionManager.addToState(UnknownValue.NO_VALUE);
            } else {
                Value allTogether = new AndValue().append(states.toArray(Value[]::new));
                Value.FilterResult filterResult = allTogether.filter(Value.FilterMode.ACCEPT, VariableProperties::localVariableFilter);
                conditionManager.addToState(filterResult.rest);
            }
        }

        // first, assignments and @NotNull
        Set<String> movedUp = new HashSet<>();

        for (Map.Entry<String, List<VariableProperties>> entry : contextsPerVariable.entrySet()) {
            String name = entry.getKey();
            List<VariableProperties> assignmentContexts = trimContexts(entry.getValue());

            OldAboutVariable localAv = variableProperties.get(name);
            boolean movedUpFirstOne = localAv == null;
            if (movedUpFirstOne) {
                localAv = assignmentContexts.stream().map(vp -> vp.variableProperties.get(name)).findFirst().orElseThrow();
                variableProperties.put(name, localAv);
                movedUp.add(name);
                assignmentContexts.remove(0);
                boolean done = assignmentContexts.isEmpty();
                log(VARIABLE_PROPERTIES, "--- variable {}: had to make a local copy; done? {}", name, done);
                if (done) {
                    continue;
                }
            }
            // depending on whether there is an assignment everywhere, or there is one which has been guaranteed to be executed
            // we keep track of the current values as well...
            boolean atLeastOneAssignmentGuaranteedToBeReached = atLeastOneAssignmentGuaranteedToBeReached(assignmentContexts, name);
            String copied = atLeastOneAssignmentGuaranteedToBeReached ? "copied" : "merged";
            log(VARIABLE_PROPERTIES, "--- variable {}: properties " + copied + " into parent context", name);


            if (!atLeastOneAssignmentGuaranteedToBeReached || assignmentContexts.size() > 1) {
                // the resulting value will be a variable value, so we must copy properties
                // for the NOT_NULL property, we may want to skip certain contexts depending on the conditional if that conditional is relevant
                // the example situation is
                //  a = initialValue; if(a == null) a = valueContext1;
                // in this particular case, for NOT_NULL only, we must skip the initialValue

                // now copy the properties that are linked to the assignment
                boolean includeThis = checkIfWithConditional(assignmentContexts) && noBlockMayBeExecuted && !atLeastOneAssignmentGuaranteedToBeReached;

                IntStream intStream = streamBuilder(assignmentContexts, name, includeThis, movedUpFirstOne, VariableProperty.NOT_NULL);
                int worstValue = intStream.min().orElse(Level.DELAY);
                if (worstValue > Level.DELAY) {
                    localAv.setProperty(VariableProperty.NOT_NULL, worstValue);
                }
                Value variableValue = new VariableValue(this, localAv.variable, localAv.name);
                localAv.setCurrentValue(variableValue, stateOfValue(localAv.variable, variableValue), ObjectFlow.NO_FLOW);
            } else {
                // single context, guaranteed to be reached; include this has become irrelevant
                OldAboutVariable av = assignmentContexts.get(0).variableProperties.get(name);
                Value singleValue = av.getCurrentValue();
                log(VARIABLE_PROPERTIES, "--- variable {}: value set to {}", singleValue);
                localAv.setCurrentValue(singleValue, stateOfValue(localAv.variable, singleValue), av.getObjectFlow());
                if (singleValue.isInstanceOf(VariableValue.class)) {
                    int notNull = assignmentContexts.get(0).getProperty(singleValue, VariableProperty.NOT_NULL);
                    localAv.setProperty(VariableProperty.NOT_NULL, notNull);
                }
            }
        }

        // so now we've dealt with all the variables which were assigned. copying back properties

        Set<String> allVariableNames = evaluationContextsGathered.stream()
                .flatMap(ec -> ec.variableProperties.keySet().stream()).collect(Collectors.toSet());
        log(VARIABLE_PROPERTIES, "Coping back other properties of {}", allVariableNames);
        for (String name : allVariableNames) {
            OldAboutVariable localAv = variableProperties.get(name);
            boolean movedUpFirstOne = localAv == null;
            if (movedUpFirstOne) {
                localAv = evaluationContextsGathered.stream()
                        .filter(vp -> vp.variableProperties.containsKey(name))
                        .map(vp -> vp.variableProperties.get(name)).findFirst().orElseThrow();
                variableProperties.put(name, localAv);
            }
            boolean notMovedUp = !movedUpFirstOne && !movedUp.contains(name);

            // copying the BEST properties, relatively straightforward

            for (VariableProperty variableProperty : BEST) {
                IntStream intStream = streamBuilder(evaluationContextsGathered, name, true, movedUpFirstOne, variableProperty);
                int bestValue = intStream.max().orElse(Level.DELAY);
                if (bestValue > Level.DELAY) {
                    localAv.setProperty(variableProperty, bestValue);
                }
            }

            // finally, copying those where we may need to increment (READ, ASSIGNED)
            // this is more complicated: we do an increment over those that are guaranteed to be executed, followed by a BEST

            for (VariableProperty variableProperty : INCREMENT_LEVEL) {
                boolean includeThis = notMovedUp && localAv.haveProperty(variableProperty);
                IntStream intStreamInc = streamBuilderInc(evaluationContextsGathered, name, includeThis, variableProperty);
                int increasedValue = intStreamInc.reduce(Level.DELAY, (v1, v2) -> {
                    int best = Level.best(v1, v2);
                    if (best == Level.READ_ASSIGN_ONCE) {
                        return Level.READ_ASSIGN_MULTIPLE_TIMES;
                    }
                    return best;
                });
                IntStream intStreamBest = streamBuilderBest(increasedValue, evaluationContextsGathered, name, variableProperty);
                int bestValue = intStreamBest.max().orElse(Level.DELAY);
                if (bestValue > Level.DELAY) {
                    localAv.setProperty(variableProperty, bestValue);
                }
            }
        }
    }

    // return true if you want the original value to be taken into account for the NOT_NULL computation
    // upon returning false, the value is only taken from the assignment contexts
    private boolean checkIfWithConditional(List<VariableProperties> assignmentContexts) {
        return true;// TODO
    }

    // we drop all contexts that come BEFORE one that is guaranteed to be executed
    // e.g. in a try { 1 } catch { 2 } finally { 3 } 1 and 2 have to go, because the assignment in 3 will overwrite anyway

    private static List<VariableProperties> trimContexts(List<VariableProperties> contexts) {
        for (int i = contexts.size() - 1; i >= 0; i--) {
            VariableProperties vp = contexts.get(i);
            if (vp.guaranteedToBeReachedByParentStatement) return contexts.subList(i, contexts.size());
        }
        return contexts;
    }

    private static boolean atLeastOneAssignmentGuaranteedToBeReached(List<VariableProperties> contexts, String name) {
        VariableProperties first = contexts.get(0);
        return first.guaranteedToBeReachedByParentStatement
                && first.variableProperties.get(name).getProperty(LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED) == Level.TRUE;
    }

    // go over those that are only potentially executed;  READ, ASSIGNED
    private IntStream streamBuilderBest(int bestValue,
                                        List<VariableProperties> evaluationContexts,
                                        String name,
                                        VariableProperty variableProperty) {
        IntStream s1 = IntStream.of(bestValue);
        IntStream s2 = evaluationContexts.stream()
                .filter(ec -> ec.variableProperties.containsKey(name)) // for more efficiency: in the assignment case, this filter is not needed
                .filter(ec -> !ec.guaranteedToBeReachedByParentStatement)
                .mapToInt(ec -> ec.variableProperties.get(name).getProperty(variableProperty));
        return IntStream.concat(s1, s2);
    }

    // do the existing one, if it's there, and those guaranteed to be executed; READ, ASSIGNED
    private IntStream streamBuilderInc(List<VariableProperties> evaluationContexts,
                                       String name,
                                       boolean includeThis,
                                       VariableProperty variableProperty) {
        IntStream s1 = evaluationContexts.stream()
                .filter(ec -> ec.variableProperties.containsKey(name)) // for more efficiency: in the assignment case, this filter is not needed
                .filter(ec -> ec.guaranteedToBeReachedByParentStatement)
                .mapToInt(ec -> ec.variableProperties.get(name).getProperty(variableProperty));
        IntStream s2 = includeThis ? IntStream.of(getPropertyPotentiallyFromCurrentValue(name, variableProperty)) : IntStream.of();
        return IntStream.concat(s1, s2);
    }

    // for those in WORST and BEST set
    private IntStream streamBuilder(List<VariableProperties> evaluationContexts,
                                    String name,
                                    boolean includeThis,
                                    boolean movedUpFirstOne,
                                    VariableProperty variableProperty) {
        IntStream s1 = evaluationContexts.stream()
                .filter(ec -> ec.variableProperties.containsKey(name)) // for more efficiency: in the assignment case, this filter is not needed
                .mapToInt(ec -> ec.getPropertyPotentiallyFromCurrentValue(name, variableProperty));
        IntStream s2 = includeThis
                ? IntStream.of(getPropertyPotentiallyFromCurrentValue(name, variableProperty))
                : IntStream.of();
        return IntStream.concat(movedUpFirstOne ? s1.skip(1) : s1, s2);
    }

    private static final EnumSet<VariableProperty> ON_VALUE = EnumSet.of(VariableProperty.NOT_NULL, IMMUTABLE, VariableProperty.CONTAINER);

    private int getPropertyPotentiallyFromCurrentValue(String name, VariableProperty variableProperty) {
        if (ON_VALUE.contains(variableProperty)) {
            return getProperty(variableProperties.get(name).getCurrentValue(), variableProperty);
        }
        return variableProperties.get(name).getProperty(variableProperty);
    }

    public boolean isKnown(Variable variable) {
        String name = variableName(variable);
        if (name == null) return false;
        return find(name) != null;
    }

    public void removeAll(List<String> toRemove) {
        variableProperties.keySet().removeAll(toRemove);
    }

    @Override
    public int getProperty(Variable variable, VariableProperty variableProperty) {
        OldAboutVariable aboutVariable = findComplain(variable);
        if (VariableProperty.NOT_NULL == variableProperty && conditionManager.isNotNull(variable)) {
            return Level.best(MultiLevel.EFFECTIVELY_NOT_NULL, aboutVariable.getProperty(variableProperty));
        }
        if (VariableProperty.SIZE == variableProperty) {
            Value sizeRestriction = conditionManager.individualSizeRestrictions().get(variable);
            if (sizeRestriction != null) {
                return sizeRestriction.encodedSizeRestriction();
            }
        }
        if (IDENTITY == variableProperty && aboutVariable.variable instanceof ParameterInfo) {
            return ((ParameterInfo) aboutVariable.variable).index == 0 ? Level.TRUE : Level.FALSE;
        }
        return aboutVariable.getProperty(variableProperty);
    }

    public int getProperty(Value value, VariableProperty variableProperty) {
        // IMPORTANT: here we do not want to catch VariableValues wrapped in the PropertyWrapper
        if (value instanceof VariableValue) {
            return getProperty(((VariableValue) value).variable, variableProperty);
        }
        // the following situation occurs when the state contains, e.g., not (null == map.get(a)),
        // and we need to evaluate the NOT_NULL property in the return transfer value in StatementAnalyser
        // See TestSMapList; also, same situation, we may have a conditional value, and the condition will decide...
        // TODO smells like dedicated code
        if (!(value.isInstanceOf(ConstantValue.class)) && conditionManager.haveNonEmptyState() && !conditionManager.inErrorState()) {
            if (VariableProperty.NOT_NULL == variableProperty) {
                int notNull = conditionManager.notNull(value);
                if (notNull != Level.DELAY) return notNull;
            }
            // TODO add SIZE support?
        }
        // redirect to Value.getProperty()
        // this is the only usage of this method; all other evaluation of a Value in an evaluation context
        // must go via the current method
        return value.getProperty(this, variableProperty);
    }

    @Override
    public void modifyingMethodAccess(Variable variable) {
        conditionManager.modifyingMethodAccess(variable);
    }

    // there is special consideration for parameters of inline values, which are NOT KNOWN to the variable properties map.
    // findComplain would actually complain when a reEvaluation takes place.
    @Override
    public boolean equals(Variable variable, Variable other) {
        String name = variableName(variable);
        String nameOther = variableName(other);

        // we allow find( ) to fail (typically comparisons out of scope)
        if (!(variable instanceof ParameterInfo)) {
            OldAboutVariable av = find(variable);
            if (av != null && av.fieldReferenceState == MULTI_COPY) return false;
        }
        if (!(other instanceof ParameterInfo)) {
            OldAboutVariable avOther = find(other);
            if (avOther != null && avOther.fieldReferenceState == MULTI_COPY) return false;
        }
        return name.equals(nameOther);
    }

    private OldAboutVariable ensureLocalCopy(Variable variable) {
        OldAboutVariable master = findComplain(variable);
        if (!variableProperties.containsKey(master.name)) {
            // we'll make a local copy
            OldAboutVariable copy = master.localCopy();
            variableProperties.put(copy.name, copy);
            return copy;
        }
        return master;
    }

    @Override
    public void assignmentBasics(Variable at, Value value, boolean assignmentToNonEmptyExpression) {
        // assignment to local variable: could be that we're in the block where it was created, then nothing happens
        // but when we're down in some descendant block, a local AboutVariable block is created (we MAY have to undo...)
        OldAboutVariable aboutVariable = ensureLocalCopy(at);

        if (assignmentToNonEmptyExpression) {
            aboutVariable.removeProperties(VariableProperty.REMOVE_AFTER_ASSIGNMENT);

            Instance instance;
            VariableValue variableValue;
            if ((instance = value.asInstanceOf(Instance.class)) != null) {
                resetToNewInstance(aboutVariable, instance);
            } else if ((variableValue = value.asInstanceOf(VariableValue.class)) != null) {
                OldAboutVariable other = findComplain(variableValue.variable);
                if (other.fieldReferenceState == SINGLE_COPY) {
                    aboutVariable.setCurrentValue(value, stateOfValue(at, value), value.getObjectFlow());
                } else if (other.fieldReferenceState == EFFECTIVELY_FINAL_DELAYED) {
                    aboutVariable.setCurrentValue(UnknownValue.NO_VALUE, UnknownValue.EMPTY, ObjectFlow.NO_FLOW);
                } else {
                    resetToUnknownValue(aboutVariable);
                }
            } else {
                aboutVariable.setCurrentValue(value, stateOfValue(at, value), value.getObjectFlow());
            }
            int assigned = aboutVariable.getProperty(VariableProperty.ASSIGNED);
            aboutVariable.setProperty(VariableProperty.ASSIGNED, Level.incrementReadAssigned(assigned));

            aboutVariable.setProperty(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT, Level.TRUE);
            aboutVariable.setProperty(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED,
                    Level.fromBool(guaranteedToBeReached(aboutVariable)));
            conditionManager.variableReassigned(at);
        }
    }

    public boolean guaranteedToBeReached(OldAboutVariable aboutVariable) {
        if (!guaranteedToBeReachedInCurrentBlock) return false;
        return recursivelyCheckGuaranteedToBeReachedByParent(aboutVariable.name);
    }

    private boolean recursivelyCheckGuaranteedToBeReachedByParent(String name) {
        if (variableProperties.containsKey(name)) {
            return true; // this is the level where we are defined
        }
        if (!guaranteedToBeReachedByParentStatement) return false;
        if (parent != null) return parent.recursivelyCheckGuaranteedToBeReachedByParent(name);
        return true;
    }

    @Override
    public void markRead(Variable variable) {
        OldAboutVariable aboutVariable = findComplain(variable);
        aboutVariable.markRead();
    }

    @Override
    public void markRead(String variableName) {
        OldAboutVariable aboutVariable = find(variableName);
        if (aboutVariable != null) {
            aboutVariable.markRead();
        }
    }

    @Override
    public Value arrayVariableValue(Value array, Value indexValue, ParameterizedType parameterizedType, Set<Variable> dependencies, Variable arrayVariable) {
        String name = ArrayAccess.dependentVariableName(array, indexValue);
        OldAboutVariable aboutVariable = find(name);
        if (aboutVariable != null) return aboutVariable.getCurrentValue();
        String arrayName = arrayVariable == null ? null : variableName(arrayVariable);
        DependentVariable dependentVariable = new DependentVariable(parameterizedType, ImmutableSet.copyOf(dependencies), name, arrayName);
        if (!isKnown(dependentVariable)) {
            createLocalVariableOrParameter(dependentVariable);
        }
        return currentValue(dependentVariable);
    }

    /**
     * here we have multiple situations, we could have something like method(a,b)[3], which will not be
     * easy to work with. Only when method has no side effects, and a, b stay identical, we could do something about this
     *
     * @param arrayAccess the input, consisting of two expressions
     * @return a new type of variable which is dependent on other variables; as soon as one is assigned to, this one
     * loses its meaning
     */

    @Override
    public DependentVariable ensureArrayVariable(ArrayAccess arrayAccess, String name, Variable arrayVariable) {
        Set<Variable> dependencies = new HashSet<>(arrayAccess.expression.variables());
        dependencies.addAll(arrayAccess.index.variables());
        ParameterizedType parameterizedType = arrayAccess.expression.returnType();
        String arrayName = arrayVariable == null ? null : variableName(arrayVariable);
        DependentVariable dependentVariable = new DependentVariable(parameterizedType, ImmutableSet.copyOf(dependencies), name, arrayName);
        if (!isKnown(dependentVariable)) {
            createLocalVariableOrParameter(dependentVariable);
        }
        return dependentVariable;
    }



    private Location location() {
        if (currentStatement != null) return new Location(currentMethod, currentStatement.index);
        if (currentMethod != null) return new Location(currentMethod);
        return new Location(currentType);
    }

    @Override
    public void raiseError(String error) {
        if (currentStatement != null && !currentStatement.inErrorState()) {
            Message message = Message.newMessage(location(), error);
            messages.add(message);
            currentStatement.errorValue.set(true);
        }
    }

    @Override
    public void raiseError(String error, String extra) {
        if (currentStatement != null && !currentStatement.inErrorState()) {
            Message message = Message.newMessage(location(), error, extra);
            messages.add(message);
            currentStatement.errorValue.set(true);
        }
    }

    @Override
    public void addPropertyRestriction(Variable variable, VariableProperty property, int value) {
        addProperty(variable, property, value);
        if (variable instanceof ParameterInfo) {
            ((ParameterInfo) variable).parameterAnalysis.get().improveProperty(property, value);
        }
        Value current = currentValue(variable);
        VariableValue variableValue;
        if ((variableValue = current.asInstanceOf(VariableValue.class)) != null) {
            addProperty(variableValue.variable, property, value);
            if (variableValue.variable instanceof ParameterInfo) {
                ((ParameterInfo) variableValue.variable).parameterAnalysis.get().improveProperty(property, value);
            }
        }
    }

    @Override
    public ObjectFlow getObjectFlow(Variable variable) {
        OldAboutVariable aboutVariable = findComplain(variable);
        return aboutVariable.getObjectFlow();
    }

    @Override
    public void updateObjectFlow(Variable variable, ObjectFlow objectFlow) {
        OldAboutVariable aboutVariable = findComplain(variable);
        aboutVariable.setObjectFlow(objectFlow);
    }

    @Override
    public ObjectFlow addAccess(boolean modifying, Access access, Value value) {
        if (value.getObjectFlow() == ObjectFlow.NO_FLOW) return value.getObjectFlow();
        ObjectFlow potentiallySplit = splitIfNeeded(value);
        if (modifying) {
            log(OBJECT_FLOW, "Set modifying access on {}", potentiallySplit);
            potentiallySplit.setModifyingAccess((MethodAccess) access);
        } else {
            log(OBJECT_FLOW, "Added non-modifying access to {}", potentiallySplit);
            potentiallySplit.addNonModifyingAccess(access);
        }
        return potentiallySplit;
    }

    @Override
    public ObjectFlow addCallOut(boolean modifying, ObjectFlow callOut, Value value) {
        if (callOut == ObjectFlow.NO_FLOW || value.getObjectFlow() == ObjectFlow.NO_FLOW) return value.getObjectFlow();
        ObjectFlow potentiallySplit = splitIfNeeded(value);
        if (modifying) {
            log(OBJECT_FLOW, "Set call-out on {}", potentiallySplit);
            potentiallySplit.setModifyingCallOut(callOut);
        } else {
            log(OBJECT_FLOW, "Added non-modifying call-out to {}", potentiallySplit);
            potentiallySplit.addNonModifyingCallOut(callOut);
        }
        return potentiallySplit;
    }

    private ObjectFlow splitIfNeeded(Value value) {
        ObjectFlow objectFlow = value.getObjectFlow();
        if (objectFlow == ObjectFlow.NO_FLOW) return objectFlow; // not doing anything
        if (objectFlow.haveModifying()) {
            // we'll need to split
            ObjectFlow split = createInternalObjectFlow(objectFlow.type, Origin.INTERNAL);
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

    public Value getCurrentState() {
        return conditionManager.getState();
    }

    public void setDelayedEvaluation(boolean delayedEvaluation) {
        this.delayedEvaluation = delayedEvaluation;
    }

    public boolean delayedState() {
        return conditionManager.delayedState() || delayedEvaluation;
    }

    public Set<String> allUnqualifiedVariableNames() {
        Set<String> fromParent;
        if (parent == null) {
            fromParent = currentType.accessibleFieldsStream().map(fieldInfo -> fieldInfo.name).collect(Collectors.toSet());
        } else {
            fromParent = parent.allUnqualifiedVariableNames();
        }
        Set<String> local = variableProperties.values().stream()
                .filter(OldAboutVariable::isNotLocalCopy)
                .map(av -> av.name).collect(Collectors.toSet());
        return SetUtil.immutableUnion(fromParent, local);
    }


    public PatternMatcher getPatternMatcher() {
        return patternMatcher;
    }
}
