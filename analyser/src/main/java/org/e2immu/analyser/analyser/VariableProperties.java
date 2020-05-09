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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

// used in MethodAnalyser

class VariableProperties implements EvaluationContext {


    static class AboutVariable {
        final Map<VariableProperty, Integer> properties = new HashMap<>();
        @NotNull
        private Value currentValue;

        private final AboutVariable localCopyOf;
        private final Value initialValue;
        private final Value resetValue;
        public final Variable variable;
        public final String name;

        private AboutVariable(Variable variable, String name, AboutVariable localCopyOf, Value initialValue, Value currentValue) {
            this.localCopyOf = localCopyOf;
            this.initialValue = initialValue;
            this.currentValue = currentValue;
            this.resetValue = currentValue;
            this.variable = variable;
            this.name = name; // the value used to put it in the map
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("props=").append(properties);
            if (currentValue != null) {
                sb.append(", currentValue=").append(currentValue);
            }
            return sb.toString();
        }

        public Value getCurrentValue() {
            return currentValue;
        }

        AboutVariable localCopy() {
            AboutVariable av = new AboutVariable(variable, name, this, initialValue, currentValue);
            av.properties.putAll(properties);
            return av;
        }

        public boolean isLocalCopy() {
            return localCopyOf != null;
        }

        public boolean isNotLocalCopy() {
            return localCopyOf == null;
        }

        public int getProperty(VariableProperty variableProperty) {
            return properties.getOrDefault(variableProperty, Level.DELAY);
        }

        void setCurrentValue(Value value) {
            this.currentValue = value;
        }

        public void setProperty(VariableProperty variableProperty, int value) {
            properties.put(variableProperty, value);
        }

        public void removeProperty(VariableProperty variableProperty) {
            properties.remove(variableProperty);
        }
    }

    private final Map<String, AboutVariable> variableProperties = new HashMap<>(); // at their level, 1x per var

    final DependencyGraph<Variable> dependencyGraphBestCase;
    final DependencyGraph<Variable> dependencyGraphWorstCase;

    final VariableProperties parent;
    Value conditional; // any conditional added to this block
    private boolean guaranteedToBeReachedInCurrentBlock = true;
    final boolean guaranteedToBeReachedByParentStatement;
    final Runnable uponUsingConditional;
    final TypeContext typeContext;
    private final MethodInfo currentMethod;
    private final TypeInfo currentType;
    final boolean inSyncBlock;

    public VariableProperties(TypeContext typeContext, TypeInfo currentType) {
        this(typeContext, currentType, null);
    }

    public VariableProperties(TypeContext typeContext, MethodInfo currentMethod) {
        this(typeContext, currentMethod.typeInfo, currentMethod);
    }

    private VariableProperties(TypeContext typeContext, TypeInfo currentType, MethodInfo currentMethod) {
        this.parent = null;
        conditional = null;
        uponUsingConditional = null;
        this.typeContext = typeContext;
        this.currentMethod = currentMethod;
        this.currentType = currentType;
        this.dependencyGraphBestCase = new DependencyGraph<>();
        this.dependencyGraphWorstCase = new DependencyGraph<>();
        guaranteedToBeReachedByParentStatement = true;
        inSyncBlock = currentMethod != null && currentMethod.isSynchronized();
    }

    public VariableProperties copyWithCurrentMethod(MethodInfo methodInfo) {
        return new VariableProperties(this, methodInfo, conditional, uponUsingConditional,
                methodInfo.isSynchronized(),
                guaranteedToBeReachedByParentStatement);
    }

    private VariableProperties(VariableProperties parent, MethodInfo currentMethod, Value conditional, Runnable uponUsingConditional,
                               boolean inSyncBlock,
                               boolean guaranteedToBeReachedByParentStatement) {
        this.parent = parent;
        this.uponUsingConditional = uponUsingConditional;
        this.conditional = conditional;
        this.typeContext = parent.typeContext;
        this.currentMethod = currentMethod;
        this.currentType = parent.currentType;
        dependencyGraphBestCase = parent.dependencyGraphBestCase;
        dependencyGraphWorstCase = parent.dependencyGraphWorstCase;
        this.inSyncBlock = inSyncBlock;
        this.guaranteedToBeReachedByParentStatement = guaranteedToBeReachedByParentStatement;
    }

    @Override
    public void linkVariables(Variable from, Set<Variable> toBestCase, Set<Variable> toWorstCase) {
        dependencyGraphBestCase.addNode(from, ImmutableList.copyOf(toBestCase));
        dependencyGraphWorstCase.addNode(from, ImmutableList.copyOf(toWorstCase));
    }

    @Override
    public MethodInfo getCurrentMethod() {
        return currentMethod;
    }

    @Override
    public TypeInfo getCurrentType() {
        return currentType;
    }

    @Override
    public EvaluationContext childInSyncBlock(Value conditional, Runnable uponUsingConditional,
                                              boolean inSyncBlock,
                                              boolean guaranteedToBeReachedByParentStatement) {
        return new VariableProperties(this, currentMethod, conditional, uponUsingConditional,
                inSyncBlock || this.inSyncBlock,
                guaranteedToBeReachedByParentStatement);
    }

    @Override
    public EvaluationContext child(Value conditional, Runnable uponUsingConditional,
                                   boolean guaranteedToBeReachedByParentStatement) {
        return new VariableProperties(this, currentMethod, conditional, uponUsingConditional,
                inSyncBlock,
                guaranteedToBeReachedByParentStatement);
    }

    public void addToConditional(Value value) {
        if (value != UnknownValue.UNKNOWN_VALUE) {
            if (conditional == UnknownValue.UNKNOWN_VALUE || conditional == null) conditional = value;
            else {
                if (conditional instanceof AndValue) {
                    conditional = ((AndValue) conditional).append(value);
                } else {
                    conditional = new AndValue().append(conditional, value);
                }
            }
        }
    }

    public Collection<AboutVariable> variableProperties() {
        return variableProperties.values();
    }

    AboutVariable findComplain(@NotNull Variable variable) {
        AboutVariable aboutVariable = find(variable);
        if (aboutVariable != null) {
            return aboutVariable;
        }
        createVariableIfRelevant(variable);
        AboutVariable aboutVariable2ndAttempt = find(variable);
        if (aboutVariable2ndAttempt != null) {
            return aboutVariable2ndAttempt;
        }
        throw new UnsupportedOperationException("Cannot find variable " + variable.detailedString());
    }

    private AboutVariable find(@NotNull Variable variable) {
        String name = variableName(variable, true);
        if (name == null) return null;
        return find(name);
    }

    private String nameOfField(FieldReference fieldReference) {
        String prefix = fieldReference.scope == null ? currentType.simpleName :
                ((This) fieldReference.scope).typeInfo.simpleName;
        return prefix + "." + fieldReference.fieldInfo.name;
    }

    private AboutVariable find(String name) {
        VariableProperties level = this;
        while (level != null) {
            AboutVariable aboutVariable = level.variableProperties.get(name);
            if (aboutVariable != null) return aboutVariable;
            level = level.parent;
        }
        return null;
    }

    // there should be 2 methods here: one called during evaluation of rhs,
    // one called on assignment sides
    public void createVariableIfRelevant(Variable variable) {
        String name = variableName(variable, true);
        if (name == null) return; // not allowed to be in the map
        if (find(name) != null) return;
        if (variable instanceof FieldReference) {
            FieldReference fieldReference = (FieldReference) variable;
            Value resetValue;
            Boolean isFinal = fieldReference.fieldInfo.isEffectivelyFinal(typeContext);
            if (isFinal == null) {
                resetValue = UnknownValue.NO_VALUE; // delay
            } else if (isFinal) {
                if (fieldReference.fieldInfo.fieldAnalysis.effectivelyFinalValue.isSet()) {
                    resetValue = fieldReference.fieldInfo.fieldAnalysis.effectivelyFinalValue.get();
                } else {
                    // DELAY!
                    resetValue = UnknownValue.NO_VALUE;
                }
            } else {
                // in sync block, or during construction phase; acts as LOCAL VARIABLE, so VV is justified
                resetValue = new VariableValue(variable, name);
            }
            internalCreate(variable, nameOfField(fieldReference), resetValue, resetValue, Set.of());
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public boolean isNotAllowedToBeInTheMap(FieldReference fieldReference) {
        if (fieldReference.fieldInfo.owner != currentType) return true; // outside my type... no need
        boolean effectivelyFinal = fieldReference.fieldInfo.isEffectivelyFinal(typeContext) == Boolean.TRUE;
        return (!effectivelyFinal && !inSyncBlock && !inConstructionPhase());
    }

    public String variableName(@NotNull Variable variable) {
        Objects.requireNonNull(variable);
        return variableName(variable, false);
    }

    private String variableName(Variable variable, boolean nullIfNotAllowed) {
        String name;
        if (variable instanceof FieldReference) {
            FieldReference fieldReference = (FieldReference) variable;
            // there are 3 cases: a field during construction phase, an effectively final field of the type we're analysing, and a field of a record
            if (fieldReference.scope == null || fieldReference.scope instanceof This) {
                if (isNotAllowedToBeInTheMap(fieldReference)) {
                    if (nullIfNotAllowed) return null;
                    throw new UnsupportedOperationException("Field not allowed to be in map");
                }
                name = nameOfField(fieldReference);
            } else {
                // we now expect the scope to be some other variable, of a private, nested type
                TypeInfo typeInfo = fieldReference.scope.parameterizedType().typeInfo;
                if (!typeInfo.isRecord()) { // by definition in the innerOuter Type hierarchy
                    throw new UnsupportedOperationException("Expected a field of a record: " + fieldReference.detailedString());
                }
                name = fieldReference.scope.name() + "." + fieldReference.fieldInfo.name;
            }
        } else {
            // parameter, local variable
            name = variable.name();
        }
        log(VARIABLE_PROPERTIES, "Resolved variable {} to {}", variable.detailedString(), name);
        return name;
    }

    @Override
    public void createLocalVariableOrParameter(@NotNull Variable variable, VariableProperty... initialProperties) {
        Set<VariableProperty> initialPropertiesAsSet = Set.of(initialProperties);
        if (variable instanceof LocalVariableReference || variable instanceof ParameterInfo) {
            Value resetValue = new VariableValue(variable, variable.name());
            internalCreate(variable, variable.name(), resetValue, resetValue, initialPropertiesAsSet);
        } else if (variable instanceof This) {
            throw new UnsupportedOperationException("Not allowed to add This to the variable properties map");
        } else {
            // field; only three cases allowed:
            // (1) a normal field during construction phase; acts like a local variable
            // (2) a non-final field inside a synchronisation block; acts like a local variable temporarily
            // (3) an effectively final field
            FieldReference fieldReference = (FieldReference) variable;
            boolean effectivelyFinal = fieldReference.fieldInfo.isEffectivelyFinal(typeContext) == Boolean.TRUE;
            if (effectivelyFinal || inSyncBlock || fieldReference.scope == null || fieldReference.scope instanceof This) {
                if (!effectivelyFinal && !inSyncBlock && !inConstructionPhase()) {
                    throw new UnsupportedOperationException("Normal field is only in variable properties during construction phase: "
                            + fieldReference.detailedString());
                }
                String name = nameOfField(fieldReference);
                Value resetValue = new VariableValue(fieldReference, name);
                internalCreate(variable, name, resetValue, resetValue, initialPropertiesAsSet);
            } else {
                throw new UnsupportedOperationException("?? cannot create other fields myself");
            }
        }
    }

    private void internalCreate(Variable variable, String name, Value initialValue, Value resetValue, Set<VariableProperty> initialProperties) {
        AboutVariable aboutVariable = new AboutVariable(variable, Objects.requireNonNull(name), null, Objects.requireNonNull(initialValue),
                Objects.requireNonNull(resetValue));
        initialProperties.forEach(variableProperty -> aboutVariable.properties.put(variableProperty, Level.TRUE));
        if (variableProperties.put(name, aboutVariable) != null)
            throw new UnsupportedOperationException("?? Duplicating name " + name);
        log(VARIABLE_PROPERTIES, "Added variable to map: {}", name);

        // regardless of whether we're a field, a parameter or a local variable...
        if (isRecordType(variable)) {
            TypeInfo recordType = variable.parameterizedType().typeInfo;
            for (FieldInfo recordField : recordType.typeInspection.get().fields) {
                String newName = name + "." + recordField.name;
                FieldReference fieldReference = new FieldReference(recordField, variable);
                Variable newVariable = new RecordField(fieldReference, newName);
                Expression initialiser = computeInitialiser(recordField);
                Value newInitialValue = computeInitialValue(recordField, initialiser);
                Value newResetValue = new VariableValue(newVariable, newName);
                internalCreate(newVariable, newName, newInitialValue, newResetValue, Set.of());
            }
        }
    }

    private static boolean isRecordType(Variable variable) {
        return !(variable instanceof This) && variable.parameterizedType().typeInfo != null && variable.parameterizedType().typeInfo.isRecord();
    }

    private Value computeInitialValue(FieldInfo recordField, Expression initialiser) {
        if (recordField.fieldAnalysis.effectivelyFinalValue.isSet()) {
            return recordField.fieldAnalysis.effectivelyFinalValue.get();
        }
        if (initialiser instanceof EmptyExpression) {
            return recordField.type.defaultValue();
        }
        return initialiser.evaluate(this, (p1, p2, p3, p4) -> {
        });// completely outside the context, but we should try
    }

    private static Expression computeInitialiser(FieldInfo recordField) {
        FieldInspection recordFieldInspection = recordField.fieldInspection.get();
        if (recordFieldInspection.initialiser.isSet()) {
            return recordFieldInspection.initialiser.get().initialiser;
        }
        return EmptyExpression.EMPTY_EXPRESSION;
    }

    @Override
    public void setValue(@NotNull Variable variable, @NotNull Value value) {
        AboutVariable aboutVariable = findComplain(variable);
        aboutVariable.currentValue = Objects.requireNonNull(value);
    }

    public void addProperty(Variable variable, VariableProperty variableProperty, int value) {
        AboutVariable aboutVariable = find(variable);
        if (aboutVariable == null) return;
        Integer current = aboutVariable.properties.get(variableProperty);
        if (current == null || current < value) {
            aboutVariable.properties.put(variableProperty, value);
        }
    }

    private static List<String> variableNamesOfLocalRecordVariables(AboutVariable aboutVariable) {
        TypeInfo recordType = aboutVariable.variable.parameterizedType().typeInfo;
        return recordType.typeInspection.get().fields.stream()
                .map(fieldInfo -> aboutVariable.name + "." + fieldInfo.name).collect(Collectors.toList());
    }

    // same as addProperty, but "descend" into fields of records as well
    // it is important that "variable" is not used to create VariableValue or so, given that it might be a "superficial" copy

    public void addPropertyAlsoRecords(Variable variable, VariableProperty variableProperty, int value) {
        AboutVariable aboutVariable = find(variable);
        if (aboutVariable == null) return; //not known to us, ignoring!
        recursivelyAddPropertyAlsoRecords(aboutVariable, variableProperty, value);
    }

    private void recursivelyAddPropertyAlsoRecords(AboutVariable aboutVariable, VariableProperty variableProperty, int value) {
        aboutVariable.properties.put(variableProperty, value);
        if (isRecordType(aboutVariable.variable)) {
            for (String name : variableNamesOfLocalRecordVariables(aboutVariable)) {
                AboutVariable aboutLocalVariable = Objects.requireNonNull(find(name));
                recursivelyAddPropertyAlsoRecords(aboutLocalVariable, variableProperty, value);
            }
        }
    }

    public void reset(Variable variable, boolean toInitialValue) {
        AboutVariable aboutVariable = find(variable);
        if (aboutVariable == null) return; //not known to us, ignoring! (symmetric to add)
        recursivelyReset(aboutVariable, toInitialValue);
    }

    private void recursivelyReset(AboutVariable aboutVariable, boolean toInitialValue) {
        aboutVariable.properties.removeAll(List.of(CHECK_NOT_NULL));
        aboutVariable.currentValue = toInitialValue ? aboutVariable.initialValue : aboutVariable.resetValue;
        if (isRecordType(aboutVariable.variable)) {
            List<String> recordNames = variableNamesOfLocalRecordVariables(aboutVariable);
            for (String name : recordNames) {
                AboutVariable aboutLocalVariable = Objects.requireNonNull(find(name));
                recursivelyReset(aboutLocalVariable, toInitialValue);
            }
        }
    }

    public boolean removeProperty(Variable variable, VariableProperty variableProperty) {
        AboutVariable aboutVariable = find(variable);
        if (aboutVariable == null) return true; //not known to us, ignoring! (symmetric to add)
        return aboutVariable.properties.remove(variableProperty);
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
        if (variable instanceof This) {
            return new ThisValue((This) variable);
        }
        if (variable instanceof FieldReference) {
            if (isNotAllowedToBeInTheMap((FieldReference) variable)) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                Boolean isFinal = fieldInfo.isEffectivelyFinal(typeContext);
                if (isFinal == null) {
                    return UnknownValue.NO_VALUE;
                }
                if (isFinal) {
                    if (fieldInfo.hasBeenDefined()) {
                        return fieldInfo.fieldAnalysis.effectivelyFinalValue.isSet() ?
                                fieldInfo.fieldAnalysis.effectivelyFinalValue.get() :
                                UnknownValue.NO_VALUE;
                    }
                    Boolean isNotNull = fieldInfo.isNotNull(typeContext);
                    if (isNotNull == null) return UnknownValue.NO_VALUE;
                    return new FinalFieldValue((FieldReference) variable, Set.of(), null, isNotNull);
                }
                return new Instance(fieldInfo.type, null, null, false);
            }
        }
        AboutVariable aboutVariable = findComplain(variable);
        if (aboutVariable.properties.contains(ASSIGNED_IN_LOOP)) {
            return aboutVariable.resetValue;
        }
        return aboutVariable.currentValue;
    }

    @Override
    public boolean isNotNull(Variable variable) {
        // step 1. check the conditional
        if (conditional != null) {
            Map<Variable, Boolean> isNotNull = conditional.individualNullClauses();
            if (isNotNull.get(variable) == Boolean.FALSE) {
                return true;
            }
        }
        // step 2. is the variable defined at this level? look at the properties
        String name = variableName(variable, true);
        AboutVariable aboutVariable = name == null ? null : find(name);
        if (aboutVariable != null && aboutVariable.currentValue instanceof VariableValue) {
            return aboutVariable.properties.contains(VariableProperty.CHECK_NOT_NULL);
        }
        Value currentValue = currentValue(variable);
        return currentValue.isNotNull(this) == Boolean.TRUE;
    }

    public Variable switchToValueVariable(Variable variable) {
        AboutVariable aboutVariable = find(variable);
        if (aboutVariable == null) return variable;
        if (aboutVariable.currentValue instanceof VariableValue)
            return ((VariableValue) aboutVariable.currentValue).variable;
        return variable;
    }

    public List<Variable> getNullConditionals() {
        if (conditional != null) {
            return conditional.individualNullClauses().entrySet()
                    .stream().filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey).collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public TypeContext getTypeContext() {
        return typeContext;
    }

    public Value evaluateWithConditional(Value value) {
        if (conditional == null) return value;
        if (!(conditional instanceof UnknownValue)) {
            return new AndValue().append(conditional, value);
        }
        return value;
    }

    public void setGuaranteedToBeReachedInCurrentBlock(boolean guaranteedToBeReachedInCurrentBlock) {
        this.guaranteedToBeReachedInCurrentBlock = guaranteedToBeReachedInCurrentBlock;
    }

    public boolean guaranteedToBeReached(Variable variable) {
        AboutVariable aboutVariable = findComplain(variable);
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

    public void ensureLocalCopy(Variable variable) {
        AboutVariable master = findComplain(variable);
        if (!variableProperties.containsKey(master.name)) {
            // we'll make a local copy
            AboutVariable copy = master.localCopy();
            variableProperties.put(copy.name, copy);
        }
    }

    public void copyBackLocalCopies(boolean statementsExecutedAtLeastOnce) {
        for (Map.Entry<String, AboutVariable> entry : variableProperties.entrySet()) {
            AboutVariable av = entry.getValue();
            String name = entry.getKey();
            if (av.localCopyOf != null) {
                av.localCopyOf.properties.clear();

                boolean erase;
                // if: the block is executed for sure, and the assignment which contains current value, is executed for sure,
                if (statementsExecutedAtLeastOnce && av.properties.contains(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED)) {
                    // erase when the result is a local variable, at this level
                    erase = av.currentValue instanceof LocalVariableReference && variableProperties.containsKey(name);
                } else {
                    erase = true; // block was executed conditionally, or the assignment was executed conditionally
                }
                copyConditionally(av.properties, av.localCopyOf.properties, READ, READ_MULTIPLE_TIMES, ASSIGNED,
                        ASSIGNED_MULTIPLE_TIMES, CONTENT_MODIFIED);
                if (erase) {
                    av.localCopyOf.currentValue = av.localCopyOf.resetValue;
                    boolean notNullHere = av.properties.contains(CHECK_NOT_NULL);
                    boolean notNullAtOrigin = av.localCopyOf.properties.contains(CHECK_NOT_NULL);
                    if (notNullHere && !notNullAtOrigin) {
                        av.localCopyOf.properties.add(CHECK_NOT_NULL);
                    } else if (!notNullHere && notNullAtOrigin) {
                        av.localCopyOf.properties.remove(CHECK_NOT_NULL);
                    }
                    log(VARIABLE_PROPERTIES, "Erasing the value of {}, merge properties to {}", name);
                } else {
                    av.localCopyOf.currentValue = av.currentValue;
                    log(VARIABLE_PROPERTIES, "Copied back value {} of {}, merge properties to {}",
                            av.currentValue, name, av.properties);
                }
            }
        }
    }

    private void copyConditionally(Set<VariableProperty> from, Set<VariableProperty> to, VariableProperty...
            properties) {
        for (VariableProperty property : properties) {
            if (from.contains(property)) to.add(property);
        }
    }

    public boolean isKnown(Variable variable) {
        String name = variableName(variable, true);
        if (name == null) return false;
        return find(name) != null;
    }

    public boolean inConstructionPhase() {
        return currentMethod != null && currentMethod.methodAnalysis.partOfConstruction.get();
    }

    public void removeAll(List<String> toRemove) {
        variableProperties.keySet().removeAll(toRemove);
    }

    public void copyProperties(Variable from, Variable to) {
        // TODO
    }

    @Override
    public int getProperty(Variable variable, VariableProperty variableProperty) {
        AboutVariable aboutVariable = findComplain(variable);
        return aboutVariable.getProperty(variableProperty);
    }
}
