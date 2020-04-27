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
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.VARIABLE_PROPERTIES;
import static org.e2immu.analyser.util.Logger.log;

// used in MethodAnalyser

class VariableProperties implements EvaluationContext {

    static class AboutVariable {
        final Set<VariableProperty> properties = new HashSet<>();
        @NotNull
        private Value currentValue = UnknownValue.NO_VALUE;

        private AboutVariable localCopyOf;

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

        void setCurrentValue(Value value) {
            this.currentValue = value;
        }

        AboutVariable localCopy() {
            AboutVariable av = new AboutVariable();
            av.localCopyOf = this;
            av.properties.addAll(properties);
            av.currentValue = currentValue;
            return av;
        }

        public boolean isLocalCopy() {
            return localCopyOf != null;
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
    final This thisVariable;

    public VariableProperties(TypeContext typeContext, This thisVariable, MethodInfo currentMethod) {
        this.parent = null;
        this.root = this;
        conditional = null;
        uponUsingConditional = null;
        this.typeContext = typeContext;
        this.currentMethod = currentMethod;
        this.thisVariable = thisVariable;
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
        thisVariable = parent.thisVariable;
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
                    TypeInfo typeInfo = fieldReference.scope.parameterizedType().typeInfo;
                    Optional<TypeInfo> theType = currentMethod.typeInfo.inTypeInnerOuterHierarchy(typeInfo);
                    if (theType.isPresent()) {
                        target = new FieldReference(fieldReference.fieldInfo, new This(theType.get()));
                        log(ANALYSER, "In VP: replacing {} by {}", variable.detailedString(), target.detailedString());
                    } else {
                        if (!complain) return null;
                        throw new UnsupportedOperationException("Ignoring " + variable.detailedString() + " -- not in type hierarchy");
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
        AboutVariable aboutVariable = new AboutVariable();
        aboutVariable.currentValue = Objects.requireNonNull(initialValue);
        aboutVariable.properties.addAll(Arrays.asList(initialProperties));
        if (variableProperties.put(Objects.requireNonNull(variable), aboutVariable) != null)
            throw new UnsupportedOperationException();
        log(VARIABLE_PROPERTIES, "Created variable {}", variable.detailedString());
    }

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
        return aboutVariable.currentValue;
    }

    @Override
    public boolean isNotNull(Variable variable) {
        // step 1. check the conditional
        if (conditional != null) {
            Optional<Variable> isNotNull = conditional.variableIsNotNull();
            if (isNotNull.isPresent() && isNotNull.get() == variable) {
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

    public List<Value> getNullConditionals() {
        if (conditional != null) {
            return conditional.individualNullClauses();
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

    public void copyBackLocalCopies() {
        for (Map.Entry<Variable, AboutVariable> entry : variableProperties.entrySet()) {
            AboutVariable av = entry.getValue();
            if (av.localCopyOf != null) {
                av.localCopyOf.currentValue = av.currentValue;
                av.properties.clear();
                av.properties.addAll(av.localCopyOf.properties);
                log(VARIABLE_PROPERTIES, "Copied back value {} and local properties {} of {}",
                        av.currentValue,
                        av.properties,
                        entry.getKey().detailedString());
            }
        }
    }

}
