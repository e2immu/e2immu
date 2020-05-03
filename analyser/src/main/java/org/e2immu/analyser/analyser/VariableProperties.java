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
import org.e2immu.analyser.model.abstractvalue.AndValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.LocalVariableModifier;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Primitives;
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
        final Set<VariableProperty> properties = new HashSet<>();
        @NotNull
        private Value currentValue = UnknownValue.NO_VALUE;

        private final AboutVariable localCopyOf;
        private final Value initialValue;
        private final Value resetValue;

        private AboutVariable(AboutVariable localCopyOf, Value initialValue, Value currentValue) {
            this.localCopyOf = localCopyOf;
            this.initialValue = initialValue;
            this.currentValue = currentValue;
            this.resetValue = currentValue;
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
            AboutVariable av = new AboutVariable(this, initialValue, currentValue);
            av.properties.addAll(properties);
            return av;
        }

        public boolean isNotLocalCopy() {
            return localCopyOf == null;
        }
    }

    final Map<Variable, AboutVariable> variableProperties = new HashMap<>(); // at their level, 1x per var

    final DependencyGraph<Variable> dependencyGraphBestCase;
    final DependencyGraph<Variable> dependencyGraphWorstCase;

    final VariableProperties parent;
    final VariableProperties root;
    Value conditional; // any conditional added to this block
    private boolean guaranteedToBeReachedInCurrentBlock = true;
    final boolean guaranteedToBeReachedByParentStatement;
    final Runnable uponUsingConditional;
    final TypeContext typeContext;
    final MethodInfo currentMethod;

    public VariableProperties(TypeContext typeContext, MethodInfo currentMethod) {
        this.parent = null;
        this.root = this;
        conditional = null;
        uponUsingConditional = null;
        this.typeContext = typeContext;
        this.currentMethod = currentMethod;
        this.dependencyGraphBestCase = new DependencyGraph<>();
        this.dependencyGraphWorstCase = new DependencyGraph<>();
        guaranteedToBeReachedByParentStatement = true;
    }

    public VariableProperties copyWithCurrentMethod(MethodInfo methodInfo) {
        return new VariableProperties(this, methodInfo, conditional, uponUsingConditional, guaranteedToBeReachedByParentStatement);
    }

    private VariableProperties(VariableProperties parent, MethodInfo currentMethod, Value conditional, Runnable uponUsingConditional, boolean guaranteedToBeReachedByParentStatement) {
        this.parent = parent;
        this.root = parent.root;
        this.uponUsingConditional = uponUsingConditional;
        this.conditional = conditional;
        this.typeContext = parent.typeContext;
        this.currentMethod = currentMethod;
        dependencyGraphBestCase = parent.dependencyGraphBestCase;
        dependencyGraphWorstCase = parent.dependencyGraphWorstCase;
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
    public EvaluationContext child(Value conditional, Runnable uponUsingConditional, boolean guaranteedToBeReachedByParentStatement) {
        return new VariableProperties(this, currentMethod, conditional, uponUsingConditional, guaranteedToBeReachedByParentStatement);
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

    private AboutVariable find(@NotNull Variable variable, boolean complain) {
        Variable target;
        Objects.requireNonNull(variable);
        if (variable instanceof FieldReference && currentMethod != null) {
            FieldReference fieldReference = (FieldReference) variable;
            if (!(fieldReference.scope instanceof This) && fieldReference.scope != null) {
                if (fieldReference.scope.parameterizedType().typeInfo != null) {
                    // node.data..., either we redirect into a field reference on the type of 'node', or ...
                    TypeInfo typeInfo = fieldReference.scope.parameterizedType().typeInfo;
                    if (typeInfo.isRecord()) { // by definition in the innerOuter Type hierarchy
                        String name = fieldReference.scope.name() + "." + fieldReference.fieldInfo.name;
                        if (fieldReference.scope instanceof ParameterInfo || fieldReference.scope instanceof LocalVariableReference) {
                            target = new LocalVariableReference(new LocalVariable(name), List.of());
                            log(VARIABLE_PROPERTIES, "Replace {} with local variable {}", variable.detailedString(), name);
                        } else { // field reference itself
                            TypeInfo owner = ((FieldReference) fieldReference.scope).fieldInfo.owner;
                            target = new FieldReference(new FieldInfo(Primitives.PRIMITIVES.voidParameterizedType, name, owner), null);
                            log(VARIABLE_PROPERTIES, "Replace {} with local field {} in {}", variable.detailedString(), name, owner.fullyQualifiedName);
                        }
                    } else {
                        Optional<TypeInfo> theType = currentMethod.typeInfo.inTypeInnerOuterHierarchy(typeInfo);
                        if (theType.isPresent()) {
                            target = new FieldReference(fieldReference.fieldInfo, null);
                            log(VARIABLE_PROPERTIES, "Replace {} with actual field {}", variable.detailedString(), fieldReference.fieldInfo.fullyQualifiedName());
                        } else {
                            if (!complain) return null;
                            throw new UnsupportedOperationException("Ignoring " + variable.detailedString() + " -- not in type hierarchy");
                        }
                    }
                } else {
                    if (!complain) return null;
                    throw new UnsupportedOperationException("Ignoring " + variable.detailedString() + ", not yet implemented");
                }
            } else target = variable;
        } else target = variable;
        // NOTE: ParameterInfo variables are at the root, UNLESS they've been created by Lambda Blocks...
        // so we cannot take the shortcut.
        if (target instanceof FieldReference) {
            AboutVariable aboutVariable = root.variableProperties.get(target);
            if (aboutVariable == null && complain) {
                throw new UnsupportedOperationException("Cannot find variable " + target.detailedString());
            }
            return aboutVariable;
        }
        AboutVariable aboutVariable = variableProperties.get(target);
        if (aboutVariable != null) return aboutVariable;
        if (parent != null) return parent.find(target, complain);
        if (!complain) return null;
        throw new UnsupportedOperationException("Cannot find variable " + target.detailedString());
    }

    /**
     * this method is the only way of adding variables to the VariableProperties class
     *
     * @param variable          the variable to add
     * @param initialProperties initial properties about this variable
     */
    @Override
    public void create(@NotNull Variable variable, @NotNull Value initialValue, VariableProperty... initialProperties) {
        AboutVariable aboutVariable = new AboutVariable(null, Objects.requireNonNull(initialValue),
                new VariableValue(variable));
        aboutVariable.properties.addAll(Arrays.asList(initialProperties));
        if (variableProperties.put(Objects.requireNonNull(variable), aboutVariable) != null)
            throw new UnsupportedOperationException();
        log(VARIABLE_PROPERTIES, "Created variable {}", variable.detailedString());

        // regardless of whether we're a field, a parameter or a local variable...
        if (isRecordType(variable)) {
            // we will create local variables (if variable is parameter or local variable) or extra fields (if field) for each of the fields of the record
            // they'll need their initialisation as well
            TypeInfo recordType = variable.parameterizedType().typeInfo;
            boolean createLocalVariable = variable instanceof LocalVariableReference || variable instanceof ParameterInfo;
            for (FieldInfo recordField : recordType.typeInspection.get().fields) {
                Variable newVariable;

                String name = variable.name() + "." + recordField.name;
                FieldInspection recordFieldInspection = recordField.fieldInspection.get();
                Expression initialiser = computeInitialiser(recordField);

                if (createLocalVariable) {
                    LocalVariable.LocalVariableBuilder localVariableBuilder = new LocalVariable.LocalVariableBuilder()
                            .setName(name)
                            .setParameterizedType(recordField.type);
                    if (recordFieldInspection.modifiers.contains(FieldModifier.FINAL)) {
                        localVariableBuilder.addModifier(LocalVariableModifier.FINAL);
                    }
                    List<Expression> initialisers;
                    if (initialiser instanceof EmptyExpression) {
                        initialisers = List.of();
                    } else {
                        initialisers = List.of(initialiser);
                    }
                    newVariable = new LocalVariableReference(localVariableBuilder.build(), initialisers);
                } else {
                    FieldInfo newField = new FieldInfo(recordField.type, name, ((FieldReference) variable).fieldInfo.owner);
                    FieldInspection.FieldInspectionBuilder builder = new FieldInspection.FieldInspectionBuilder();
                    if (!(initialiser instanceof EmptyExpression)) {
                        builder.setInitializer(initialiser);
                    }
                    if (recordFieldInspection.modifiers.contains(FieldModifier.FINAL)) {
                        builder.addModifier(FieldModifier.FINAL);
                    }
                    builder.addModifier(FieldModifier.PRIVATE);
                    // TODO copy some more annotations?
                    newField.fieldInspection.set(builder.build());
                    Variable scope = ((FieldReference) variable).scope;
                    newVariable = new FieldReference(newField, scope);
                }
                Value newInitialValue = computeInitialValue(recordField, initialiser);
                VariableProperty[] newInitialProperties = {};
                create(newVariable, newInitialValue, newInitialProperties);
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

    private Expression computeInitialiser(FieldInfo recordField) {
        FieldInspection recordFieldInspection = recordField.fieldInspection.get();
        if (recordFieldInspection.initialiser.isSet()) {
            return recordFieldInspection.initialiser.get().initialiser;
        }
        return EmptyExpression.EMPTY_EXPRESSION;
    }

    @Override
    public void setValue(@NotNull Variable variable, @NotNull Value value) {
        AboutVariable aboutVariable = find(variable, true);
        assert aboutVariable != null; // to keep intellij happy, because of the complain we know it cannot be null
        aboutVariable.currentValue = Objects.requireNonNull(value);
    }

    public boolean addProperty(Variable variable, VariableProperty variableProperty) {
        AboutVariable aboutVariable = find(variable, false);
        if (aboutVariable == null) return true; //not known to us, ignoring!
        return aboutVariable.properties.add(variableProperty);
    }

    // same as addProperty, but "descend" into fields of records as well
    // it is important that "variable" is not used to create VariableValue or so, given that it might be a "superficial" copy
    public boolean addPropertyAlsoRecords(Variable variable, VariableProperty variableProperty) {
        AboutVariable aboutVariable = find(variable, false);
        if (aboutVariable == null) return true; //not known to us, ignoring!
        boolean added = aboutVariable.properties.add(variableProperty);
        if (isRecordType(variable)) {
            List<Variable> recordFields = superficialLocalVariableObjects(variable);
            return recordFields.stream().map(v -> addPropertyAlsoRecords(v, variableProperty)).reduce(added, (b1, b2) -> b1 || b2);
        }
        return added;
    }

    // superficial here means: just enough for the equality operators to work, to find the variable
    private List<Variable> superficialLocalVariableObjects(Variable variable) {
        TypeInfo recordType = variable.parameterizedType().typeInfo;
        List<Variable> list = new ArrayList<>();
        boolean createLocalVariable = variable instanceof LocalVariableReference || variable instanceof ParameterInfo;
        for (FieldInfo recordField : recordType.typeInspection.get().fields) {
            String name = variable.name() + "." + recordField.name;
            Variable newVariable;
            if (createLocalVariable) {
                newVariable = new LocalVariableReference(new LocalVariable(name), List.of());
            } else {
                TypeInfo owner = ((FieldReference) variable).fieldInfo.owner;
                newVariable = new FieldReference(new FieldInfo(Primitives.PRIMITIVES.voidParameterizedType, name, owner), null);
            }
            list.add(newVariable);
        }
        return list;
    }

    // it is important that "variable" is not used to create VariableValue or so, given that it might be a "superficial" copy
    public void reset(Variable variable, boolean toInitialValue) {
        AboutVariable aboutVariable = find(variable, false);
        if (aboutVariable == null) return; //not known to us, ignoring! (symmetric to add)
        aboutVariable.properties.removeAll(List.of(PERMANENTLY_NOT_NULL, CHECK_NOT_NULL));
        aboutVariable.currentValue = toInitialValue ? aboutVariable.initialValue : aboutVariable.resetValue;
        if (isRecordType(variable)) {
            List<Variable> recordFields = superficialLocalVariableObjects(variable);
            recordFields.forEach(v -> reset(v, toInitialValue));
        }
    }

    public boolean removeProperty(Variable variable, VariableProperty variableProperty) {
        AboutVariable aboutVariable = find(variable, false);
        if (aboutVariable == null) return true; //not known to us, ignoring! (symmetric to add)
        return aboutVariable.properties.remove(variableProperty);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (parent == null) sb.append("@Root: ");
        else sb.append(parent.toString()).append("; ");
        sb.append(variableProperties.entrySet().stream()
                .map(e -> e.getKey().detailedString() + ": " + e.getValue())
                .collect(Collectors.joining(", ")));
        return sb.toString();
    }

    @Override
    @NotNull
    public Value currentValue(Variable variable) {
        AboutVariable aboutVariable = find(variable, false);
        if (aboutVariable == null) {
            if (variable instanceof FieldReference) {
                FieldReference fieldReference = (FieldReference) variable;
                ParameterizedType type = fieldReference.fieldInfo.type;
                if (type.typeInfo != null && type.typeInfo.primaryType().equals(currentMethod.typeInfo.primaryType())) {
                    throw new UnsupportedOperationException("Coming across reference to field of my own primary type?" +
                            " should have been declared: " + variable.detailedString());
                }
                root.create(variable, new VariableValue(variable));
                aboutVariable = Objects.requireNonNull(find(variable, true));
            } else {
                throw new UnsupportedOperationException("Coming across variable that should have been declared: " +
                        variable.detailedString());
            }
        }
        if (aboutVariable.properties.contains(ASSIGNED_IN_LOOP)) {
            return new VariableValue(variable);
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
        AboutVariable aboutVariable = variableProperties.get(variable);
        if (aboutVariable != null) {
            return aboutVariable.properties.contains(VariableProperty.CHECK_NOT_NULL)
                    || aboutVariable.properties.contains(VariableProperty.PERMANENTLY_NOT_NULL);
        }
        return parent != null && parent.isNotNull(variable);
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
        if (!guaranteedToBeReachedInCurrentBlock) return false;
        return recursivelyCheckGuaranteedToBeReachedByParent(variable);
    }

    private boolean recursivelyCheckGuaranteedToBeReachedByParent(Variable variable) {
        if (variableProperties.containsKey(variable)) {
            return true; // this is the level where we are defined
        }
        if (!guaranteedToBeReachedByParentStatement) return false;
        if (parent != null) return parent.recursivelyCheckGuaranteedToBeReachedByParent(variable);
        return true;
    }


    public void ensureLocalCopy(Variable variable) {
        AboutVariable aboutVariable = variableProperties.get(variable);
        if (aboutVariable == null) {
            // we'll make a local copy
            AboutVariable master = find(variable, true);
            assert master != null;
            AboutVariable copy = master.localCopy();
            variableProperties.put(variable, copy);
        }
    }

    public void copyBackLocalCopies(boolean statementsExecutedAtLeastOnce) {
        for (Map.Entry<Variable, AboutVariable> entry : variableProperties.entrySet()) {
            AboutVariable av = entry.getValue();
            Variable variable = entry.getKey();
            if (av.localCopyOf != null) {
                av.localCopyOf.properties.clear();

                boolean erase;
                // if: the block is executed for sure, and the assignment which contains current value, is executed for sure,
                if (statementsExecutedAtLeastOnce && av.properties.contains(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED)) {
                    // erase when the result is a local variable, at this level
                    erase = av.currentValue instanceof LocalVariableReference && variableProperties.containsKey(variable);
                } else {
                    erase = true; // block was executed conditionally, or the assignment was executed conditionally
                }
                copyConditionally(av.properties, av.localCopyOf.properties, READ, READ_MULTIPLE_TIMES, ASSIGNED,
                        ASSIGNED_MULTIPLE_TIMES, PERMANENTLY_NOT_NULL, CONTENT_MODIFIED);
                if (erase) {
                    av.localCopyOf.currentValue = new VariableValue(variable);
                    boolean notNullHere = av.properties.contains(CHECK_NOT_NULL);
                    boolean notNullAtOrigin = av.localCopyOf.properties.contains(CHECK_NOT_NULL);
                    if (notNullHere && !notNullAtOrigin) {
                        av.localCopyOf.properties.add(CHECK_NOT_NULL);
                    } else if (!notNullHere && notNullAtOrigin) {
                        av.localCopyOf.properties.remove(CHECK_NOT_NULL);
                    }
                    log(VARIABLE_PROPERTIES, "Erasing the value of {}, merge properties to {}", variable.detailedString());
                } else {
                    av.localCopyOf.currentValue = av.currentValue;
                    log(VARIABLE_PROPERTIES, "Copied back value {} of {}, merge properties to {}",
                            av.currentValue,
                            variable.detailedString(), av.properties);
                }
            }
        }
    }

    private void copyConditionally(Set<VariableProperty> from, Set<VariableProperty> to, VariableProperty... properties) {
        for (VariableProperty property : properties) {
            if (from.contains(property)) to.add(property);
        }
    }

    @Override
    public void setNotNull(Variable variable) {
        addProperty(variable, CHECK_NOT_NULL);
    }

    public boolean isKnown(Variable variable) {
        return find(variable, false) != null;
    }

}
