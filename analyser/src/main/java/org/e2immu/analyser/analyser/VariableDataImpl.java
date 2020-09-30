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

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.VariableInfo.FieldReferenceState.SINGLE_COPY;
import static org.e2immu.analyser.util.Logger.LogTarget.VARIABLE_PROPERTIES;
import static org.e2immu.analyser.util.Logger.log;

@E2Container
public class VariableDataImpl implements VariableData {

    public final Map<String, VariableInfo> variables;
    public final DependencyGraph<Variable> dependencyGraph;

    private VariableDataImpl(Map<String, VariableInfo> variables, DependencyGraph<Variable> dependencyGraph) {
        this.variables = ImmutableMap.copyOf(variables);
        this.dependencyGraph = dependencyGraph;
        dependencyGraph.freeze();
    }

    public Iterable<VariableInfo> variableInfos() {
        return variables.values();
    }

    @Override
    public boolean isDelaysInDependencyGraph() {
        return false;
    }

    @Override
    public DependencyGraph<Variable> getDependencyGraph() {
        return dependencyGraph;
    }

    @Override
    public Set<Map.Entry<String, VariableInfoImpl.Builder>> variables() {
        return variables.entrySet();
    }

    @Container(builds = VariableDataImpl.class)
    public static class Builder {

        private final Map<String, VariableInfoImpl.Builder> variables = new HashMap<>();
        private final DependencyGraph<Variable> dependencyGraph = new DependencyGraph<>();
        private boolean delaysInDependencyGraph;


        public Set<Map.Entry<String, VariableInfoImpl.Builder>> variables() {
            return variables.entrySet();
        }


        public DependencyGraph<Variable> getDependencyGraph() {
            return dependencyGraph;
        }

        public Collection<VariableInfoImpl.Builder> variableInfos() {
            return variables.values();
        }


        public void createLocalVariableOrParameter(Variable variable) {
            if (variable instanceof LocalVariableReference || variable instanceof ParameterInfo || variable instanceof DependentVariable) {
                internalCreate(variable, variable.name(), UnknownValue.NO_VALUE, UnknownValue.NO_VALUE, SINGLE_COPY);
            } else {
                throw new UnsupportedOperationException("Not allowed to add This or FieldReference using this method");
            }
        }

        private void internalCreate(Variable variable,
                                    String name,
                                    Value initialValue,
                                    Value resetValue,
                                    VariableInfo.FieldReferenceState fieldReferenceState) {

        }

        public void addProperty(Variable variable, VariableProperty variableProperty, int value) {
            VariableInfoImpl.Builder variableInfo = findComplain(variable);
            variableInfo.setProperty(variableProperty, value);
        }

        public boolean isLocalVariable(VariableInfo variableInfo) {
            if (variableInfo.isLocalVariableReference()) return true;
            if (variableInfo.isLocalCopy() && variableInfo.getLocalCopyOf().isLocalVariableReference())
                return true;
            if (variableInfo.getVariable() instanceof DependentVariable) {
                DependentVariable dependentVariable = (DependentVariable) variableInfo.getVariable();
                if (dependentVariable.arrayName != null) {
                    VariableInfo avArray = find(dependentVariable.arrayName);
                    return avArray != null && isLocalVariable(avArray);
                }
            }
            return false;
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


        public VariableInfo ensureFieldReference(FieldReference fieldReference) {
            String name = variableName(fieldReference);
            VariableInfoImpl.Builder av = find(name);
            if (find(name) != null) return;
            Value resetValue;
            StatementAnalysis.FieldReferenceState fieldReferenceState = singleCopy(fieldReference);
            if (fieldReferenceState == StatementAnalysis.FieldReferenceState.EFFECTIVELY_FINAL_DELAYED) {
                resetValue = UnknownValue.NO_VALUE; // delay
            } else if (fieldReferenceState == StatementAnalysis.FieldReferenceState.MULTI_COPY) {
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

        public void ensureThisVariable(This thisVariable) {
            String name = variableName(thisVariable);
            if (find(name) != null) return;
            VariableValue resetValue = new VariableValue(this, thisVariable, name);
            internalCreate(thisVariable, name, resetValue, resetValue, StatementAnalysis.FieldReferenceState.SINGLE_COPY);
        }

        private VariableInfoImpl.Builder findComplain(@NotNull Variable variable) {
            VariableInfo aboutVariable = find(variable);
            if (aboutVariable != null) {
                return aboutVariable;
            }
            if (variable instanceof FieldReference) {
                ensureFieldReference((FieldReference) variable);
            } else if (variable instanceof This) {
                ensureThisVariable((This) variable);
            }
            AboutVariable aboutVariable2ndAttempt = find(variable);
            if (aboutVariable2ndAttempt != null) {
                return aboutVariable2ndAttempt;
            }
            throw new UnsupportedOperationException("Cannot find variable " + variable.detailedString());
        }


        VariableInfoImpl.Builder find(@NotNull Variable variable) {
            String name = variableName(variable);
            if (name == null) return null;
            return find(name);
        }

        private VariableInfoImpl.Builder find(String name) {
            return variables.get(name);
        }


        public boolean isDelaysInDependencyGraph() {
            return delaysInDependencyGraph;
        }

        public VariableDataImpl build() {
            return new VariableDataImpl(variables.entrySet().stream().
                    collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().build())), dependencyGraph);
        }


        public void removeAllVariables(List<String> toRemove) {
            variables.keySet().removeAll(toRemove);
        }

        public void initialise(Collection<ParameterAnalyser> parameterAnalysers, StatementAnalysis statementAnalysis, boolean startOfNewBlock) {
            if (statementAnalysis == null) {
                for (ParameterAnalyser parameterAnalyser : parameterAnalysers) {
                    LocalVariableReference lvr = new LocalVariableReference(new LocalVariable(List.of(),
                            parameterAnalyser.parameterInfo.name, parameterAnalyser.parameterInfo.parameterizedType(), List.of()), List.of());
                    createLocalVariableOrParameter(lvr);
                }
                return;
            }
            for (VariableInfo variableInfo : statementAnalysis.variableData.get().variableInfos()) {
                if (startOfNewBlock) {
                    VariableInfoImpl.Builder localCopy = variableInfo.localCopy();
                    variables.put(localCopy.getName(), localCopy);
                } else {
                    variables.put(variableInfo.getName(), new VariableInfoImpl.Builder(variableInfo));
                }
            }
        }

        public int levelVariable(Variable assignmentTarget) {
            VariableInfo variableInfo = find(assignmentTarget);
            if (variableInfo.getVariable() instanceof FieldReference) return Integer.MAX_VALUE;
            int steps = 0;
            while (variableInfo != null) {
                if (variableInfo.isNotLocalCopy()) return steps;
                variableInfo = variableInfo.getLocalCopyOf();
            }
            return -1;
        }
    }
}
