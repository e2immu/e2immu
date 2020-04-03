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
import org.e2immu.analyser.util.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

// used in MethodAnalyser

class VariableProperties implements EvaluationContext {

    static class AboutVariable {
        final Set<VariableProperty> properties = new HashSet<>();
        Value currentValue;

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("props=").append(properties);
            if (currentValue != null) {
                sb.append(", currentValue=").append(currentValue);
            }
            return sb.toString();
        }
    }

    final Map<Variable, AboutVariable> variableProperties = new HashMap<>(); // at their level, 1x per var

    final DependencyGraph<Variable> dependencyGraph;
    final VariableProperties parent;
    final VariableProperties root;
    Value conditional; // any conditional added to this block
    final TypeContext typeContext;
    final MethodInfo currentMethod;
    final This thisVariable;
    final Map<Variable, Set<Variable>> variablesLinkedToFieldsAndParameters = new HashMap<>();

    public VariableProperties(TypeContext typeContext, This thisVariable, MethodInfo currentMethod) {
        this.parent = null;
        this.root = this;
        conditional = null;
        this.typeContext = typeContext;
        this.currentMethod = currentMethod;
        this.thisVariable = thisVariable;
        this.dependencyGraph = new DependencyGraph<>();
    }

    public VariableProperties(VariableProperties parent) {
        this(parent, null);
    }

    public VariableProperties(VariableProperties parent, Value conditional) {
        this.parent = parent;
        this.root = parent.root;
        this.conditional = conditional == null ? UnknownValue.UNKNOWN_VALUE : conditional;
        this.typeContext = parent.typeContext;
        this.currentMethod = parent.currentMethod;
        thisVariable = parent.thisVariable;
        dependencyGraph = parent.dependencyGraph;
    }

    @Override
    public void linkVariables(Variable from, Set<Variable> to) {
        dependencyGraph.addNode(from, ImmutableList.copyOf(to));
    }

    @Override
    public MethodInfo getCurrentMethod() {
        return currentMethod;
    }

    @Override
    public EvaluationContext child(Value conditional) {
        return new VariableProperties(this, conditional);
    }

    public void addToConditional(Value value) {
        if (value != UnknownValue.UNKNOWN_VALUE) {
            if (conditional == UnknownValue.UNKNOWN_VALUE || conditional == null) conditional = value;
            else conditional = AndValue.and(conditional, value);
        }
    }

    private AboutVariable find(Variable variable, boolean complain) {
        Variable target;
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

    public void create(Variable variable, VariableProperty... initialProperties) {
        AboutVariable aboutVariable = new AboutVariable();
        aboutVariable.properties.addAll(Arrays.asList(initialProperties));
        if (variableProperties.put(variable, aboutVariable) != null) throw new UnsupportedOperationException();
        log(Logger.LogTarget.ANALYSER, "Created variable {}", variable.detailedString());
    }

    public void setValue(Variable variable, Value value) {
        AboutVariable aboutVariable = find(variable, true);
        aboutVariable.currentValue = value;
    }

    public boolean addProperty(Variable variable, VariableProperty variableProperty) {
        AboutVariable aboutVariable = find(variable, false);
        if (aboutVariable == null) return true; //not known to us, ignoring!
        return aboutVariable.properties.add(variableProperty);
    }

    public boolean removeProperty(Variable variable, VariableProperty variableProperty) {
        AboutVariable aboutVariable = find(variable, true);
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
    public Optional<Value> get(Variable variable) {
        AboutVariable aboutVariable = find(variable, true);
        return Optional.of(Objects.requireNonNullElseGet(aboutVariable.currentValue, () -> new VariableValue(variable)));
    }

    @Override
    public boolean isNotNull(Variable variable) {
        // step 1. is the variable defined at this level? look at the properties
        AboutVariable aboutVariable = variableProperties.get(variable);
        if (aboutVariable != null) {
            return aboutVariable.properties.contains(VariableProperty.CHECK_NOT_NULL);
        }
        // step 2. check the conditional
        if (conditional != null) {
            Optional<Variable> isNotNull = conditional.variableIsNotNull();
            if (isNotNull.isPresent() && isNotNull.get() == variable) {
                return true;
            }
        }
        return parent != null && parent.isNotNull(variable);
    }

    public List<Value> getNullConditionals() {
        List<Value> list = new ArrayList<>();
        recursiveNullConditionals(list, conditional);
        return list;
    }

    private void recursiveNullConditionals(List<Value> list, Value value) {
        if (value instanceof AndValue) {
            recursiveNullConditionals(list, ((AndValue) value).lhs);
            recursiveNullConditionals(list, ((AndValue) value).rhs);
        }
        if (value != null) {
            if (value.variableIsNull().isPresent()) list.add(value);
            if (value.variableIsNotNull().isPresent()) list.add(value);
        }
    }

    @Override
    public TypeContext getTypeContext() {
        return typeContext;
    }
}
