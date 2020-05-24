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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.ArrayAccess;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.analyser.util.SMapList;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.e2immu.analyser.analyser.AboutVariable.FieldReferenceState.*;
import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

// used in MethodAnalyser

class VariableProperties implements EvaluationContext {

    // the following two variables can be assigned to as we progress through the statements

    private Value conditional; // any conditional added to this block
    private boolean guaranteedToBeReachedInCurrentBlock = true;
    private NumberedStatement currentStatement;

    // all the rest is final

    // these 2 will be modified by the statement analyser

    final DependencyGraph<Variable> dependencyGraphBestCase;
    final DependencyGraph<Variable> dependencyGraphWorstCase;

    // modified by adding errors

    final TypeContext typeContext;

    // the rest should be not modified

    final int depth;
    final int iteration;
    final DebugConfiguration debugConfiguration;
    final VariableProperties parent;
    final boolean guaranteedToBeReachedByParentStatement;
    final Runnable uponUsingConditional;
    final MethodInfo currentMethod;
    final TypeInfo currentType;
    final boolean inSyncBlock;

    // locally modified

    private final Map<String, AboutVariable> variableProperties = new HashMap<>(); // at their level, 1x per var

    // in type analyser, for fields
    public VariableProperties(TypeContext typeContext, TypeInfo currentType, int iteration, DebugConfiguration debugConfiguration) {
        this(typeContext, currentType, iteration, debugConfiguration, null);
    }

    // in type analyser, for methods
    public VariableProperties(TypeContext typeContext, int iteration, DebugConfiguration debugConfiguration, MethodInfo currentMethod) {
        this(typeContext, currentMethod.typeInfo, iteration, debugConfiguration, currentMethod);
    }

    private VariableProperties(TypeContext typeContext,
                               TypeInfo currentType,
                               int iteration,
                               DebugConfiguration debugConfiguration,
                               MethodInfo currentMethod) {
        this.iteration = iteration;
        this.depth = 0;
        this.debugConfiguration = debugConfiguration;
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
        return new VariableProperties(this, depth, methodInfo, null, conditional, uponUsingConditional,
                methodInfo.isSynchronized(),
                guaranteedToBeReachedByParentStatement);
    }

    private VariableProperties(VariableProperties parent,
                               int depth,
                               MethodInfo currentMethod,
                               NumberedStatement currentStatement,
                               Value conditional,
                               Runnable uponUsingConditional,
                               boolean inSyncBlock,
                               boolean guaranteedToBeReachedByParentStatement) {
        this.iteration = parent.iteration;
        this.depth = depth;
        this.debugConfiguration = parent.debugConfiguration;
        this.parent = parent;
        this.uponUsingConditional = uponUsingConditional;
        this.conditional = conditional;
        this.typeContext = parent.typeContext;
        this.currentMethod = currentMethod;
        this.currentStatement = currentStatement;
        this.currentType = parent.currentType;
        dependencyGraphBestCase = parent.dependencyGraphBestCase;
        dependencyGraphWorstCase = parent.dependencyGraphWorstCase;
        this.inSyncBlock = inSyncBlock;
        this.guaranteedToBeReachedByParentStatement = guaranteedToBeReachedByParentStatement;
    }

    @Override
    public NumberedStatement getCurrentStatement() {
        return currentStatement;
    }

    public void setCurrentStatement(NumberedStatement currentStatement) {
        this.currentStatement = currentStatement;
    }

    @Override
    public int getIteration() {
        return iteration;
    }

    @Override
    public DebugConfiguration getDebugConfiguration() {
        return debugConfiguration;
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
        return new VariableProperties(this, depth + 1, currentMethod, currentStatement, conditional, uponUsingConditional,
                inSyncBlock || this.inSyncBlock,
                guaranteedToBeReachedByParentStatement);
    }

    @Override
    public EvaluationContext child(Value conditional, Runnable uponUsingConditional,
                                   boolean guaranteedToBeReachedByParentStatement) {
        return new VariableProperties(this, depth + 1, currentMethod, currentStatement, conditional, uponUsingConditional,
                inSyncBlock,
                guaranteedToBeReachedByParentStatement);
    }

    public void addToConditional(Value value) {
        if (!value.isUnknown()) {
            if (conditional == null || conditional.isUnknown()) conditional = value;
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
            ensureThisVariable((FieldReference) variable);
        } else if (variable instanceof This) {
            ensureThisVariable((This) variable);
        }
        AboutVariable aboutVariable2ndAttempt = find(variable);
        if (aboutVariable2ndAttempt != null) {
            return aboutVariable2ndAttempt;
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

    public void ensureThisVariable(FieldReference fieldReference) {
        String name = variableName(fieldReference);
        if (find(name) != null) return;
        Value resetValue;
        AboutVariable.FieldReferenceState fieldReferenceState = singleCopy(fieldReference);
        if (fieldReferenceState == EFFECTIVELY_FINAL_DELAYED) {
            resetValue = UnknownValue.NO_VALUE; // delay
        } else if (fieldReferenceState == MULTI_COPY) {
            resetValue = new VariableValue(this, fieldReference, name, true);
        } else {
            FieldAnalysis fieldAnalysis = fieldReference.fieldInfo.fieldAnalysis.get();
            int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.TRUE) {
                if (fieldAnalysis.effectivelyFinalValue.isSet()) {
                    resetValue = fieldAnalysis.effectivelyFinalValue.get();
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
        internalCreate(fieldReference, name, resetValue, resetValue, Set.of(), fieldReferenceState);
    }

    public void ensureThisVariable(This thisVariable) {
        String name = variableName(thisVariable);
        if (find(name) != null) return;
        VariableValue resetValue = new VariableValue(this, thisVariable, name);
        internalCreate(thisVariable, name, resetValue, resetValue, Set.of(), SINGLE_COPY);
    }

    private AboutVariable.FieldReferenceState singleCopy(FieldReference fieldReference) {
        int effectivelyFinal = fieldReference.fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL);
        if (effectivelyFinal == Level.DELAY) return EFFECTIVELY_FINAL_DELAYED;
        boolean isEffectivelyFinal = effectivelyFinal == Level.TRUE;
        boolean inConstructionPhase = currentMethod != null && currentMethod.methodAnalysis.get().partOfConstruction.get();
        return isEffectivelyFinal || inSyncBlock || inConstructionPhase ? SINGLE_COPY : MULTI_COPY;
    }

    @NotNull
    private String variableName(@NotNull Variable variable) {
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
            name = thisVariable.typeInfo.simpleName + ".this";
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
        if (variable instanceof LocalVariableReference || variable instanceof ParameterInfo || variable instanceof DependentVariable) {
            Value resetValue = new VariableValue(this, variable, variable.name());
            internalCreate(variable, variable.name(), resetValue, resetValue, initialPropertiesAsSet, SINGLE_COPY);
        } else {
            throw new UnsupportedOperationException("Not allowed to add This or FieldReference using this method");
        }
    }

    private void internalCreate(Variable variable,
                                String name,
                                Value initialValue,
                                Value resetValue,
                                Set<VariableProperty> initialProperties,
                                AboutVariable.FieldReferenceState fieldReferenceState) {
        AboutVariable aboutVariable = new AboutVariable(variable, Objects.requireNonNull(name), null,
                Objects.requireNonNull(initialValue),
                Objects.requireNonNull(resetValue), Objects.requireNonNull(fieldReferenceState));

        // copy properties from the field into the variable properties
        if (variable instanceof FieldReference) {
            FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
            if (fieldInfo.hasBeenDefined()) {
                fieldInfo.fieldAnalysis.get().properties.visit(aboutVariable::setProperty);
            } else {
                // the difference now is that absence of info means that the property is false
                for (VariableProperty variableProperty : INSTANCE_PROPERTIES) {
                    int value = fieldInfo.fieldAnalysis.get().properties.getOtherwise(variableProperty, Level.FALSE);
                    aboutVariable.setProperty(variableProperty, value);
                }
            }
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
        if (recordField.fieldAnalysis.get().effectivelyFinalValue.isSet()) {
            return recordField.fieldAnalysis.get().effectivelyFinalValue.get();
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


    // the difference with resetToUnknownValue is 2-fold: we check properties, and we initialise record fields
    private void resetToNewInstance(AboutVariable aboutVariable, Instance instance) {
        aboutVariable.setCurrentValue(aboutVariable.resetValue);
        for (VariableProperty variableProperty : INSTANCE_PROPERTIES) {
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
            ensureThisVariable((FieldReference) variable);
        }
        AboutVariable aboutVariable = findComplain(variable);
        return new VariableValue(this, variable, aboutVariable.name);
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
        if (!(conditional.isUnknown())) {
            return new AndValue().append(conditional, value);
        }
        return value;
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

    private static final VariableProperty[] BEST = {SIZE};
    private static final VariableProperty[] WORST_IN_ASSIGNMENT = {VariableProperty.NOT_NULL};
    private static final VariableProperty[] WORST = {VariableProperty.NOT_MODIFIED};

    private static final VariableProperty[] INCREMENT_LEVEL = {READ, ASSIGNED};

    // first we only keep those that have been assigned at the lower level
    // then we get rid of those that are local variables created at the lower level; all the rest stays
    private static final Predicate<AboutVariable> ASSIGNED_NOT_LOCAL_VAR = aboutVariable ->
            Level.value(aboutVariable.getProperty(ASSIGNED), 0) == Level.TRUE &&
                    (!aboutVariable.isLocalVariable() || aboutVariable.isLocalCopy());

    /**
     * So we have a number of sub-contexts all at the same level, some guaranteed to be executed,
     * some not. Assignments to variables of the higher level in the sub-level, have caused a local copy to be created.
     * Assignments to fields which have not been seen yet at the higher level, cause originals at the lower levels.
     *
     * @param evaluationContextsGathered the list of contexts gathered
     */
    void copyBackLocalCopies(List<VariableProperties> evaluationContextsGathered, boolean noBlockMayBeExecuted) {
        Map<String, List<VariableProperties>> contextsPerVariable = SMapList.create();
        evaluationContextsGathered
                .forEach(vp -> vp.variableProperties.entrySet().stream()
                        .filter(e -> ASSIGNED_NOT_LOCAL_VAR.test(e.getValue()))
                        .forEach(e -> SMapList.addWithArrayList(contextsPerVariable, e.getKey(), vp)));
        log(VARIABLE_PROPERTIES, "Copying back assignment and properties of {}", contextsPerVariable.keySet());

        // first, assignments and @NotNull
        Set<String> movedUp = new HashSet<>();

        for (Map.Entry<String, List<VariableProperties>> entry : contextsPerVariable.entrySet()) {
            String name = entry.getKey();
            List<VariableProperties> assignmentContexts = trimContexts(entry.getValue());

            AboutVariable localAv = variableProperties.get(name);
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

            // now copy the properties that are linked to the assignment
            boolean includeThis = noBlockMayBeExecuted && !atLeastOneAssignmentGuaranteedToBeReached;

            for (VariableProperty variableProperty : WORST_IN_ASSIGNMENT) {
                IntStream intStream = streamBuilder(assignmentContexts, name, includeThis, movedUpFirstOne, variableProperty);
                int worstValue = intStream.min().orElse(Level.DELAY);
                if (worstValue > Level.DELAY) {
                    localAv.setProperty(variableProperty, worstValue);
                }
            }

            if (!atLeastOneAssignmentGuaranteedToBeReached || assignmentContexts.size() > 1) {
                localAv.setCurrentValue(new VariableValue(this, localAv.variable, localAv.name));
            } else {
                Value singleValue = assignmentContexts.get(0).variableProperties.get(name).getCurrentValue();
                log(VARIABLE_PROPERTIES, "--- variable {}: value set to {}", singleValue);
                localAv.setCurrentValue(singleValue);
            }
        }

        // so now we've dealt with all the variables which were assigned. copying back properties

        Set<String> allVariableNames = evaluationContextsGathered.stream()
                .flatMap(ec -> ec.variableProperties.keySet().stream()).collect(Collectors.toSet());
        log(VARIABLE_PROPERTIES, "Coping back other properties of {}", allVariableNames);
        for (String name : allVariableNames) {
            AboutVariable localAv = variableProperties.get(name);
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

            // copying the WORST properties, relatively straightforward

            for (VariableProperty variableProperty : WORST) {
                IntStream intStream = streamBuilder(evaluationContextsGathered, name, true, movedUpFirstOne, variableProperty);
                int worstValue = intStream.min().orElse(Level.DELAY);
                if (worstValue > Level.DELAY) {
                    localAv.setProperty(variableProperty, worstValue);
                }
            }

            // finally, copying those where we may need to increment (READ, ASSIGNED)
            // this is more complicated: we do an increment over those that are guaranteed to be executed, followed by a BEST

            for (VariableProperty variableProperty : INCREMENT_LEVEL) {
                boolean includeThis = notMovedUp && localAv.haveProperty(variableProperty);
                IntStream intStreamInc = streamBuilderInc(evaluationContextsGathered, name, includeThis, variableProperty);
                int increasedValue = intStreamInc.reduce(Level.DELAY, (v1, v2) -> Level.nextLevelTrue(Level.best(v1, v2), 1));
                IntStream intStreamBest = streamBuilderBest(increasedValue, evaluationContextsGathered, name, variableProperty);
                int bestValue = intStreamBest.max().orElse(Level.DELAY);
                if (bestValue > Level.DELAY) {
                    localAv.setProperty(variableProperty, bestValue);
                }
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
            return variableProperties.get(name).getCurrentValue().getProperty(this, variableProperty);
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

    @Override
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

            aboutVariable.setProperty(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT, Level.TRUE);
            aboutVariable.setProperty(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED,
                    Level.fromBool(guaranteedToBeReached(aboutVariable)));

            if (conditional != null) conditional = removeNullClausesInvolving(conditional, at);
        }
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

    @Override
    public void markRead(Variable variable) {
        AboutVariable aboutVariable = findComplain(variable);
        aboutVariable.markRead();
    }

    @Override
    public void markRead(String variableName) {
        AboutVariable aboutVariable = find(variableName);
        if (aboutVariable != null) {
            aboutVariable.markRead();
        }
    }

    @Override
    public Value arrayVariableValue(Value array, Value indexValue, ParameterizedType parameterizedType, Set<Variable> dependencies, Variable arrayVariable) {
        String name = ArrayAccess.dependentVariableName(array, indexValue);
        AboutVariable aboutVariable = find(name);
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

    public boolean isLocalVariable(AboutVariable aboutVariable) {
        if (aboutVariable.isLocalVariable()) return true;
        if (aboutVariable.isLocalCopy() && aboutVariable.localCopyOf.isLocalVariable())
            return true;
        if (aboutVariable.variable instanceof DependentVariable) {
            DependentVariable dependentVariable = (DependentVariable) aboutVariable.variable;
            if (dependentVariable.arrayName != null) {
                AboutVariable avArray = find(dependentVariable.arrayName);
                return avArray != null && isLocalVariable(avArray);
            }
        }
        return false;
    }

    private Location location() {
        if (currentStatement != null) return new Location(currentMethod, currentStatement.streamIndices());
        if (currentMethod != null) return new Location(currentMethod);
        return new Location(currentType);
    }

    @Override
    public void raiseError(String error) {
        if (currentStatement != null && !currentStatement.errorValue.isSet()) {
            Message message = Message.newMessage(location(), error);
            getTypeContext().addMessage(message);
            currentStatement.errorValue.set(true);
        }
    }

    @Override
    public void raiseError(String error, String extra) {
        if (currentStatement != null && !currentStatement.errorValue.isSet()) {
            Message message = Message.newMessage(location(), error, extra);
            getTypeContext().addMessage(message);
            currentStatement.errorValue.set(true);
        }
    }

    @Override
    public void markNotNull(Variable variable) {
        AboutVariable aboutVariable = findComplain(variable);
        aboutVariable.setProperty(VariableProperty.NOT_NULL, Level.TRUE);
    }
}
