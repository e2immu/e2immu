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

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayAccess;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.CharValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.Location;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

public abstract class CommonAbstractValue {

    @BeforeClass
    public static void beforeClass() {
        Logger.activate(Logger.LogTarget.CNF);
    }

    static Variable createVariable(String name) {
        return new Variable() {
            @Override
            public ParameterizedType parameterizedType() {
                if (Set.of("a", "b", "c", "d").contains(name)) return Primitives.PRIMITIVES.booleanParameterizedType;
                if (Set.of("i", "j", "k").contains(name)) return Primitives.PRIMITIVES.intParameterizedType;
                if (Set.of("s", "t").contains(name)) return Primitives.PRIMITIVES.stringParameterizedType;
                return null;
            }

            @Override
            public ParameterizedType concreteReturnType() {
                return parameterizedType();
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String detailedString() {
                return name;
            }

            @Override
            public boolean isStatic() {
                return false;
            }

            @Override
            public SideEffect sideEffect(SideEffectContext sideEffectContext) {
                return null;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    static EvaluationContext minimalEvaluationContext = new EvaluationContext() {

        @Override
        public int getIteration() {
            return 0;
        }

        @Override
        public void addProperty(Variable variable, VariableProperty variableProperty, int value) {

        }

        @Override
        public void addPropertyRestriction(Variable variable, VariableProperty property, int value) {

        }

        @Override
        public MethodInfo getCurrentMethod() {
            throw new UnsupportedOperationException();
        }

        @Override
        public NumberedStatement getCurrentStatement() {
            return null;
        }

        @Override
        public TypeInfo getCurrentType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EvaluationContext childInSyncBlock(Value conditional, Runnable uponUsingConditional, boolean inSyncBlock, boolean guaranteedToBeReachedByParentStatement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EvaluationContext child(Value conditional, Runnable uponUsingConditional, boolean guaranteedToBeReachedByParentStatement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeContext getTypeContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void createLocalVariableOrParameter(Variable variable, VariableProperty... initialProperties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void linkVariables(Variable variableFromExpression, Set<Variable> toBestCase, Set<Variable> toWorstCase) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Value currentValue(Variable variable) {
            return new VariableValue(this, variable, variable.name(), null);
        }

        @Override
        public VariableValue newVariableValue(Variable variable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Value arrayVariableValue(Value array, Value indexValue, ParameterizedType parameterizedType, Set<Variable> dependencies, Variable arrayVariable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            return 0;
        }

        @Override
        public int getProperty(Value value, VariableProperty variableProperty) {
            return 0;
        }

        @Override
        public boolean equals(Variable variable, Variable other) {
            return variable.name().equals(other.name());
        }

        @Override
        public void merge(EvaluationContext child) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markRead(Variable variable) {
            // nothing here
        }

        @Override
        public void markRead(String variableName) {

        }

        @Override
        public DependentVariable ensureArrayVariable(ArrayAccess arrayAccess, String name, Variable arrayVariable) {
            return null;
        }

        @Override
        public void assignmentBasics(Variable at, Value value, boolean assignmentToNonEmptyExpression) {

        }

        @Override
        public void raiseError(String message) {

        }

        @Override
        public void raiseError(String message, String extra) {

        }

        @Override
        public Location getLocation() {
            return null;
        }
    };

    static final Variable va = createVariable("a");
    static final Variable vb = createVariable("b");
    static final Variable vc = createVariable("c");
    static final Variable vd = createVariable("d");
    static final VariableValue a = new VariableValue(minimalEvaluationContext, va, "a", null);
    static final VariableValue b = new VariableValue(minimalEvaluationContext, vb, "b", null);
    static final VariableValue c = new VariableValue(minimalEvaluationContext, vc, "c", null);
    static final VariableValue d = new VariableValue(minimalEvaluationContext, vd, "d", null);

    static final Variable vi = createVariable("i");
    static final Variable vj = createVariable("j");
    static final Variable vk = createVariable("k");
    static final VariableValue i = new VariableValue(minimalEvaluationContext, vi, "i", null);
    static final VariableValue j = new VariableValue(minimalEvaluationContext, vj, "j", null);
    static final VariableValue k = new VariableValue(minimalEvaluationContext, vk, "k", null);

    static final Variable vs = createVariable("s");
    static final Variable vt = createVariable("t");
    static final VariableValue s = new VariableValue(minimalEvaluationContext, vs, "s", null);
    static final VariableValue t = new VariableValue(minimalEvaluationContext, vt, "t", null);

}
