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
import org.e2immu.analyser.util.SMapList;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AboutVariable.FieldReferenceState.*;
import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

// used in MethodAnalyser

class VariableProperties implements EvaluationContext {

    // the following two variables can be assigned to as we progress through the statements

    private Value conditional; // any conditional added to this block
    private boolean guaranteedToBeReachedInCurrentBlock = true;

    // all the rest is final

    // these 2 will be modified by the statement analyser

    final DependencyGraph<Variable> dependencyGraphBestCase;
    final DependencyGraph<Variable> dependencyGraphWorstCase;

    // modified by adding errors

    final TypeContext typeContext;

    // the rest should be not modified

    final VariableProperties parent;
    final boolean guaranteedToBeReachedByParentStatement;
    final Runnable uponUsingConditional;
    final MethodInfo currentMethod;
    final TypeInfo currentType;
    final boolean inSyncBlock;

    // locally modified

    private final Map<String, AboutVariable> variableProperties = new HashMap<>(); // at their level, 1x per var

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

    private AboutVariable findComplain(@NotNull Variable variable) {
        AboutVariable aboutVariable = find(variable);
        if (aboutVariable != null) {
            return aboutVariable;
        }
        if (variable instanceof FieldReference) {
            ensureVariable((FieldReference) variable);
            AboutVariable aboutVariable2ndAttempt = find(variable);
            if (aboutVariable2ndAttempt != null) {
                return aboutVariable2ndAttempt;
            }
        }
        throw new UnsupportedOperationException("Cannot find variable " + variable.detailedString());
    }

    private AboutVariable find(@NotNull Variable variable) {
        String name = variableName(variable);
        if (name == null) return null;
        return find(name);
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

    public void ensureVariable(FieldReference fieldReference) {
        String name = variableName(fieldReference);
        if (find(name) != null) return;
        Value resetValue;
        AboutVariable.FieldReferenceState fieldReferenceState = singleCopy(fieldReference);
        if (fieldReferenceState == EFFECTIVELY_FINAL_DELAYED) {
            resetValue = UnknownValue.NO_VALUE; // delay
        } else if (fieldReferenceState == MULTI_COPY) {
            resetValue = UnknownValue.UNKNOWN_VALUE;
        } else {
            int effectivelyFinal = fieldReference.fieldInfo.fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.TRUE) {
                if (fieldReference.fieldInfo.fieldAnalysis.effectivelyFinalValue.isSet()) {
                    resetValue = fieldReference.fieldInfo.fieldAnalysis.effectivelyFinalValue.get();
                } else if (fieldReference.fieldInfo.owner.hasBeenDefined()) {
                    resetValue = UnknownValue.NO_VALUE; // delay
                } else {
                    // undefined, will never get a value, but may have decent properties
                    resetValue = new VariableValue(this, fieldReference, name);
                }
            } else {
                // local variable situation
                resetValue = new VariableValue(this, fieldReference, name);
            }
        }
        internalCreate(fieldReference, name, resetValue, resetValue, Set.of(), fieldReferenceState);
    }

    private AboutVariable.FieldReferenceState singleCopy(FieldReference fieldReference) {
        int effectivelyFinal = fieldReference.fieldInfo.fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effectivelyFinal == Level.DELAY) return EFFECTIVELY_FINAL_DELAYED;
        boolean isEffectivelyFinal = effectivelyFinal == Level.TRUE;
        boolean inConstructionPhase = currentMethod != null && currentMethod.methodAnalysis.partOfConstruction.get();
        return isEffectivelyFinal || inSyncBlock || inConstructionPhase ? SINGLE_COPY : MULTI_COPY;
    }

    @NotNull
    private String variableName(@NotNull Variable variable) {
        String name;
        if (variable instanceof FieldReference) {
            FieldReference fieldReference = (FieldReference) variable;
            // there are 3 cases: a field during construction phase, an effectively final field of the type we're analysing, and a field of a record
            if (fieldReference.scope == null) {
                name = currentType.simpleName + "." + fieldReference.fieldInfo.name;
            } else if (fieldReference.scope instanceof This) {
                name = ((This) fieldReference.scope).typeInfo.simpleName + ".this." + fieldReference.fieldInfo.name;
            } else {
                name = fieldReference.scope.name() + "." + fieldReference.fieldInfo.name;
            }
        } else if (variable instanceof This) {
            return null; // will be ignored
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
            Value resetValue = new VariableValue(this, variable, variable.name());
            internalCreate(variable, variable.name(), resetValue, resetValue, initialPropertiesAsSet, SINGLE_COPY);
        } else {
            throw new UnsupportedOperationException("Not allowed to add This or FieldReference using this method");
        }
    }

    /*
        } else {
            // field; only three cases allowed:
            // (1) a normal field during construction phase; acts like a local variable
            // (2) a non-final field inside a synchronisation block; acts like a local variable temporarily
            // (3) an effectively final field
            FieldReference fieldReference = (FieldReference) variable;
            boolean effectivelyFinal = fieldReference.fieldInfo.isEffectivelyFinal(typeContext) == Boolean.TRUE;
            if (effectivelyFinal || inSyncBlock || fieldReference.scope == null || fieldReference.scope instanceof This) {
                if (!effectivelyFinal && !inSyncBlock && notInConstructionPhase()) {
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
*/
    private void internalCreate(Variable variable,
                                String name,
                                Value initialValue,
                                Value resetValue,
                                Set<VariableProperty> initialProperties,
                                AboutVariable.FieldReferenceState fieldReferenceState) {
        AboutVariable aboutVariable = new AboutVariable(variable, Objects.requireNonNull(name), null,
                Objects.requireNonNull(initialValue),
                Objects.requireNonNull(resetValue), Objects.requireNonNull(fieldReferenceState));
        if (variable instanceof FieldReference) {
            ((FieldReference) variable).fieldInfo.fieldAnalysis.properties.visit(aboutVariable::setProperty);
        }
        // copied over the existing one
        initialProperties.forEach(variableProperty -> aboutVariable.setProperty(variableProperty, Level.TRUE));
        if (variableProperties.put(name, aboutVariable) != null) {
            throw new UnsupportedOperationException("?? Duplicating name " + name);
        }
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
                Value newResetValue = new VariableValue(this, newVariable, newName);
                internalCreate(newVariable, newName, newInitialValue, newResetValue, Set.of(), fieldReferenceState);
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

    public void addProperty(Variable variable, VariableProperty variableProperty, int value) {
        AboutVariable aboutVariable = find(variable);
        if (aboutVariable == null) return;
        int current = aboutVariable.getProperty(variableProperty);
        if (current < value) {
            aboutVariable.setProperty(variableProperty, value);
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
        aboutVariable.setProperty(variableProperty, value);
        if (isRecordType(aboutVariable.variable)) {
            for (String name : variableNamesOfLocalRecordVariables(aboutVariable)) {
                AboutVariable aboutLocalVariable = Objects.requireNonNull(find(name));
                recursivelyAddPropertyAlsoRecords(aboutLocalVariable, variableProperty, value);
            }
        }
    }

    private static final VariableProperty[] PROPS_OF_INSTANCE = {VariableProperty.NOT_NULL, IMMUTABLE, VariableProperty.CONTAINER};

    // the difference with resetToUnknownValue is 2-fold: we check properties, and we initialise record fields
    private void resetToNewInstance(AboutVariable aboutVariable, Instance instance) {
        aboutVariable.setCurrentValue(aboutVariable.resetValue);
        for (VariableProperty variableProperty : PROPS_OF_INSTANCE) {
            aboutVariable.setProperty(variableProperty, instance.getPropertyOutsideContext(variableProperty));
        }

        if (isRecordType(aboutVariable.variable)) {
            List<String> recordNames = variableNamesOfLocalRecordVariables(aboutVariable);
            for (String name : recordNames) {
                AboutVariable aboutLocalVariable = Objects.requireNonNull(find(name));
                resetToInitialValues(aboutLocalVariable);
            }
        }
    }

    private void resetToInitialValues(AboutVariable aboutVariable) {
        if (aboutVariable.initialValue instanceof Instance) {
            resetToNewInstance(aboutVariable, (Instance) aboutVariable.initialValue);
        } else {
            aboutVariable.setCurrentValue(aboutVariable.initialValue);
            if (isRecordType(aboutVariable.variable)) {
                List<String> recordNames = variableNamesOfLocalRecordVariables(aboutVariable);
                for (String name : recordNames) {
                    AboutVariable aboutLocalVariable = Objects.requireNonNull(find(name));
                    resetToInitialValues(aboutLocalVariable);
                }
            }
        }
    }

    private void resetToUnknownValue(AboutVariable aboutVariable) {
        aboutVariable.setCurrentValue(aboutVariable.resetValue);
        if (isRecordType(aboutVariable.variable)) {
            List<String> recordNames = variableNamesOfLocalRecordVariables(aboutVariable);
            for (String name : recordNames) {
                AboutVariable aboutLocalVariable = Objects.requireNonNull(find(name));
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
        if (variable instanceof This) {
            return new ThisValue((This) variable);
        }
        AboutVariable aboutVariable = findComplain(variable);
        if (aboutVariable.getProperty(ASSIGNED_IN_LOOP) == Level.TRUE) {
            return aboutVariable.resetValue;
        }
        return aboutVariable.getCurrentValue();
    }

    @Override
    public VariableValue newVariableValue(Variable variable) {
        if (variable instanceof This) throw new UnsupportedOperationException();
        if (variable instanceof FieldReference) {
            ensureVariable((FieldReference) variable);
        }
        AboutVariable aboutVariable = findComplain(variable);
        return new VariableValue(this, variable, aboutVariable.name);
    }

    @Override
    public VariableValue newArrayVariableValue(Value array, Value indexValue) {
        throw new UnsupportedOperationException(); // TODO goal is to make a local variable with a combined name
    }

    public Variable switchToValueVariable(Variable variable) {
        AboutVariable aboutVariable = find(variable);
        if (aboutVariable == null) return variable;
        Value currentValue = aboutVariable.getCurrentValue();
        if (currentValue instanceof VariableValue)
            return ((VariableValue) currentValue).variable;
        return variable;
    }

    public Set<Variable> getNullConditionals(boolean equalToNull) {
        if (conditional != null) {
            return conditional.individualNullClauses().entrySet()
                    .stream().filter(e -> e.getValue() == equalToNull)
                    .map(Map.Entry::getKey).collect(Collectors.toSet());
        }
        return Set.of();
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


    private static final VariableProperty[] BEST = {CONTENT_MODIFIED};
    private static final VariableProperty[] WORST = {VariableProperty.NOT_NULL};
    private static final VariableProperty[] INCREMENT_LEVEL = {READ, ASSIGNED};

    // first we only keep those that have been assigned at the lower level
    // then we get rid of those that are local variables created at the lower level; all the rest stays
    private static final Predicate<AboutVariable> ASSIGNED_NOT_LOCAL_VAR = aboutVariable ->
            Level.value(aboutVariable.getProperty(ASSIGNED), 0) == Level.TRUE &&
                    (aboutVariable.getProperty(CREATED) != Level.TRUE || aboutVariable.isLocalCopy());

    /**
     * So we have a number of sub-contexts all at the same level, some guaranteed to be executed,
     * some not. Assignments to variables of the higher level in the sub-level, have caused a local copy to be created.
     * Assignments to fields which have not been seen yet at the higher level, cause originals at the lower levels.
     *
     * @param evaluationContextsGathered the list of contexts gathered
     */
    public void copyBackLocalCopies(List<VariableProperties> evaluationContextsGathered, boolean noBlockMayBeExecuted) {
        Map<String, List<VariableProperties>> contextsPerVariable = SMapList.create();
        evaluationContextsGathered
                .forEach(vp -> vp.variableProperties.entrySet().stream()
                        .filter(e -> ASSIGNED_NOT_LOCAL_VAR.test(e.getValue()))
                        .forEach(e -> SMapList.addWithArrayList(contextsPerVariable, e.getKey(), vp)));
        log(VARIABLE_PROPERTIES, "Copying back variable properties of {}", contextsPerVariable.keySet());
        for (Map.Entry<String, List<VariableProperties>> entry : contextsPerVariable.entrySet()) {
            String name = entry.getKey();
            List<VariableProperties> assignmentContexts = trimContexts(entry.getValue());

            AboutVariable localAv = variableProperties.get(name);
            boolean movedUpFirstOne = localAv == null;
            if (movedUpFirstOne) {
                localAv = assignmentContexts.stream().map(vp -> vp.variableProperties.get(name)).findFirst().orElseThrow();
                variableProperties.put(name, localAv);
                assignmentContexts.remove(0);
                boolean done = assignmentContexts.isEmpty();
                log(VARIABLE_PROPERTIES, "--- variable {}: had to make a local copy; done? {}", name, done);
                if (done) {
                    //if (localAv.getProperty(ASSIGNED_IN_LOOP) == Level.TRUE) {
                    //localAv.setCurrentValue(localAv.resetValue);
                    //}
                    continue;
                }
            }
            // depending on whether there is an assignment everywhere, or there is one which has been guaranteed to be executed
            // we keep track of the current values as well...
            boolean atLeastOneBlockGuaranteedToBeReached;
            Value copySingleValue = isCopySingleValue(assignmentContexts, name);
            if (copySingleValue != null) {
                atLeastOneBlockGuaranteedToBeReached = true;
                log(VARIABLE_PROPERTIES, "--- variable {} has a single value copied into parent context", name);
                localAv.setCurrentValue(copySingleValue);
            } else {
                atLeastOneBlockGuaranteedToBeReached = assignmentContexts.get(0).guaranteedToBeReachedByParentStatement;
                String copied = atLeastOneBlockGuaranteedToBeReached ? "copied" : "merged";
                log(VARIABLE_PROPERTIES, "--- variable {} has multiple values " + copied + " into parent context", name);

                localAv.setCurrentValue(new VariableValue(this, localAv.variable, localAv.name));
            }

            // now copy all the properties; this will be property per property
            boolean includeThis = noBlockMayBeExecuted && !atLeastOneBlockGuaranteedToBeReached;
            for (VariableProperty variableProperty : BEST) {
                IntStream intStream = streamBuilder(assignmentContexts, name, includeThis, movedUpFirstOne, variableProperty);
                int bestValue = intStream.max().orElse(Level.DELAY);
                localAv.setProperty(variableProperty, bestValue);
            }
            for (VariableProperty variableProperty : INCREMENT_LEVEL) {
                IntStream intStream = streamBuilder(assignmentContexts, name, includeThis, movedUpFirstOne, variableProperty);
                int increasedValue = intStream.reduce(Level.DELAY, (v1, v2) -> Level.nextLevelTrue(Level.best(v1, v2), 1));
                localAv.setProperty(variableProperty, increasedValue);
            }
            for (VariableProperty variableProperty : WORST) {
                IntStream intStream = streamBuilder(assignmentContexts, name, includeThis, movedUpFirstOne, variableProperty);
                int worstValue = intStream
                        .peek(i -> log(VARIABLE_PROPERTIES, "Have value {}", i))
                        .min().orElse(Level.DELAY);
                localAv.setProperty(variableProperty, worstValue);
            }
        }

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

    // following trimContexts, the situation is either:
    // (A) one guaranted to be reached, and then some optionally (see try {} catch {} without finally), or
    // (B) one guaranteed to be reached only (for statement), or
    // (C) only optional ones (like if {} else {})
    // there cannot be 2 guaranteed to be reached. A single value result value is only possible in case of (B)
    private static Value isCopySingleValue(List<VariableProperties> contexts, String name) {
        if (contexts.size() != 1) return null;
        VariableProperties vp = contexts.get(0);
        AboutVariable aboutVariable = Objects.requireNonNull(vp.variableProperties.get(name));
        // the assignment has to be reached...
        if (aboutVariable.getProperty(LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED) != Level.TRUE) return null;
        // ok we can now return a value
        // if (aboutVariable.getProperty(ASSIGNED_IN_LOOP) == Level.TRUE) return aboutVariable.resetValue;
        return aboutVariable.getCurrentValue();
    }

    private IntStream streamBuilder(List<VariableProperties> evaluationContexts,
                                    String name,
                                    boolean includeThis,
                                    boolean movedUpFirstOne,
                                    VariableProperty variableProperty) {
        IntStream s1 = evaluationContexts.stream()
                .map(ec -> ec.variableProperties.get(name))
                .mapToInt(aboutVariable -> getPropertyPotentiallyFromValue(aboutVariable, variableProperty));
        IntStream s2 = includeThis
                ? IntStream.of(getPropertyPotentiallyFromValue(variableProperties.get(name), variableProperty))
                : IntStream.of();
        return IntStream.concat(movedUpFirstOne ? s1.skip(1) : s1, s2);
    }

    private static final EnumSet<VariableProperty> ON_VALUE = EnumSet.of(VariableProperty.NOT_NULL, IMMUTABLE, VariableProperty.CONTAINER);

    private int getPropertyPotentiallyFromValue(AboutVariable aboutVariable, VariableProperty variableProperty) {
        Value currentValue = aboutVariable.getCurrentValue();
        if (!(currentValue instanceof VariableValue) && ON_VALUE.contains(variableProperty)) {
            return currentValue.getProperty(this, variableProperty);
        }
        return aboutVariable.getProperty(variableProperty);
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
        AboutVariable aboutVariable = findComplain(variable);
        if (VariableProperty.NOT_NULL.equals(variableProperty)) {
            if (getNullConditionals(false).contains(variable)) {
                return Level.best(Level.compose(Level.TRUE, 0), aboutVariable.getProperty(variableProperty));
            }
        }
        return aboutVariable.getProperty(variableProperty);
    }

    @Override
    public boolean equals(Variable variable, Variable other) {
        AboutVariable av = findComplain(variable);
        if (av.fieldReferenceState == MULTI_COPY) return false;
        AboutVariable avOther = findComplain(other);
        if (avOther.fieldReferenceState == MULTI_COPY) return false;
        return av.name.equals(avOther.name);
    }

    private AboutVariable ensureLocalCopy(Variable variable) {
        AboutVariable master = findComplain(variable);
        if (!variableProperties.containsKey(master.name)) {
            // we'll make a local copy
            AboutVariable copy = master.localCopy();
            variableProperties.put(copy.name, copy);
            return copy;
        }
        return master;
    }

    public void assignmentBasics(Variable at, Value value, boolean assignmentToNonEmptyExpression) {
        // assignment to local variable: could be that we're in the block where it was created, then nothing happens
        // but when we're down in some descendant block, a local AboutVariable block is created (we MAY have to undo...)
        AboutVariable aboutVariable = ensureLocalCopy(at);

        if (assignmentToNonEmptyExpression) {
            if (value instanceof Instance) {
                resetToNewInstance(aboutVariable, (Instance) value);
            } else if (value instanceof VariableValue) {
                AboutVariable other = findComplain(((VariableValue) value).variable);
                if (other.fieldReferenceState == SINGLE_COPY) {
                    aboutVariable.setCurrentValue(value);
                } else if (other.fieldReferenceState == EFFECTIVELY_FINAL_DELAYED) {
                    aboutVariable.setCurrentValue(UnknownValue.NO_VALUE);
                } else {
                    resetToUnknownValue(aboutVariable);
                }
            } else {
                aboutVariable.setCurrentValue(value);
            }
            int assigned = aboutVariable.getProperty(VariableProperty.ASSIGNED);
            aboutVariable.setProperty(VariableProperty.ASSIGNED, Level.nextLevelTrue(assigned, 1));
        }
        if (conditional != null) conditional = removeNullClausesInvolving(conditional, at);

        // those 2 are set even if there was no real assignment; you should not create a local variable without
        // assignment, and never use it
        aboutVariable.setProperty(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT, Level.TRUE);
        aboutVariable.setProperty(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED,
                Level.fromBool(guaranteedToBeReached(aboutVariable)));
    }

    public boolean guaranteedToBeReached(AboutVariable aboutVariable) {
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

    private static Value removeNullClausesInvolving(Value conditional, Variable variable) {
        Value toTest = conditional instanceof NegatedValue ? ((NegatedValue) conditional).value : conditional;
        if (toTest instanceof EqualsValue && toTest.variables().contains(variable)) {
            return null;
        }
        if (conditional instanceof AndValue) {
            return ((AndValue) conditional).removeClausesInvolving(variable);
        }
        return conditional;
    }

    void markRead(Variable variable) {
        AboutVariable aboutVariable = findComplain(variable);
        aboutVariable.removeProperty(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT);
        int read = aboutVariable.getProperty(VariableProperty.READ);
        aboutVariable.setProperty(VariableProperty.READ, Level.nextLevelTrue(read, 1));
    }
}
