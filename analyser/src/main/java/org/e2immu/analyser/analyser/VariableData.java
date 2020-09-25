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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.ValueWithVariable;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.analyser.util.Either;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.NotNull;

import java.util.Set;

import static org.e2immu.analyser.util.Logger.LogTarget.VARIABLE_PROPERTIES;
import static org.e2immu.analyser.util.Logger.log;

public class VariableData {

    public final SetOnceMap<String, AboutVariable> variables = new SetOnceMap<>();
    public DependencyGraph<Variable> dependencyGraph;

    public Iterable<AboutVariable> variableProperties() {
    }

    public boolean isDelaysInDependencyGraph() {
    }

    static class AboutVariable {

        public final Variable variable;

        public int getProperty(VariableProperty methodDelay) {
        }

        public Value getCurrentValue() {
        }

        public Value getStateOnAssignment() {
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


    public org.e2immu.analyser.analyser.AboutVariable ensureFieldReference(FieldReference fieldReference) {
        String name = variableName(fieldReference);
        org.e2immu.analyser.analyser.AboutVariable av = find(name);
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

    private org.e2immu.analyser.analyser.AboutVariable findComplain(@NotNull Variable variable) {
        org.e2immu.analyser.analyser.AboutVariable aboutVariable = find(variable);
        if (aboutVariable != null) {
            return aboutVariable;
        }
        if (variable instanceof FieldReference) {
            ensureFieldReference((FieldReference) variable);
        } else if (variable instanceof This) {
            ensureThisVariable((This) variable);
        }
        org.e2immu.analyser.analyser.AboutVariable aboutVariable2ndAttempt = find(variable);
        if (aboutVariable2ndAttempt != null) {
            return aboutVariable2ndAttempt;
        }
        throw new UnsupportedOperationException("Cannot find variable " + variable.detailedString());
    }

    private org.e2immu.analyser.analyser.AboutVariable find(@NotNull Variable variable) {
        String name = variableName(variable);
        if (name == null) return null;
        return find(name);
    }

    private org.e2immu.analyser.analyser.AboutVariable find(String name) {
        StatementAnalysis level = this;
        while (level != null) {
            org.e2immu.analyser.analyser.AboutVariable aboutVariable = level.variables.get(name);
            if (aboutVariable != null) return aboutVariable;
            level = level.parent;
        }
        return null;
    }



    public class AddVariable implements StatementAnalysis.StatementAnalysisModification {
        private final Variable variable;

        public AddVariable(Variable variable) {
            this.variable = variable;
        }

        @Override
        public void run() {

        }
    }

    public class LinkVariable implements StatementAnalysis.StatementAnalysisModification {
        private final Variable variable;
        private final Set<Variable> to;

        public LinkVariable(Variable variable, Set<Variable> to) {
            this.variable = variable;
            this.to = to;
        }

        @Override
        public void run() {

        }
    }

    public class SetProperty implements StatementAnalysis.StatementAnalysisModification {
        private final Either<Variable, String> variable;
        private final VariableProperty property;
        private final int value;

        public SetProperty(Variable variable, VariableProperty property, int value) {
            this.value = value;
            this.property = property;
            this.variable = Either.left(variable);
        }

        public SetProperty(String variableName, VariableProperty property, int value) {
            this.value = value;
            this.property = property;
            this.variable = Either.right(variableName);
        }

        @Override
        public void run() {
            org.e2immu.analyser.analyser.AboutVariable aboutVariable = variable.isLeft() ? find(variable.getLeft()) : find(variable.getRight());
            if (aboutVariable == null) {
                if (create && variable.isLeft()) {
                    if (variable.getLeft() instanceof FieldReference)
                        aboutVariable = ensureFieldReference((FieldReference) variable.getLeft());
                } else return;
            }
            int current = aboutVariable.getProperty(property);
            if (current < value) {
                aboutVariable.setProperty(property, value);
            }

            Value currentValue = aboutVariable.getCurrentValue();
            ValueWithVariable valueWithVariable;
            if ((valueWithVariable = currentValue.asInstanceOf(ValueWithVariable.class)) == null) return;
            Variable other = valueWithVariable.variable;
            if (!variable.equals(other)) {
                addProperty(other, property, value);
            }
        }
    }
}
