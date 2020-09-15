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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.junit.BeforeClass;

import java.util.List;
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
                if (Set.of("s", "t", "p").contains(name)) return Primitives.PRIMITIVES.stringParameterizedType;
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
            public SideEffect sideEffect(EvaluationContext evaluationContext) {
                return null;
            }

            @Override
            public String toString() {
                return name;
            }

            @Override
            public int variableOrder() {
                return 5;
            }
        };
    }

    static ParameterInfo createParameter(String name) {
        if (!Primitives.PRIMITIVES.objectTypeInfo.typeInspection.isSetPotentiallyRun()) {
            Primitives.PRIMITIVES.objectTypeInfo.typeInspection.set(new TypeInspection.TypeInspectionBuilder()
                    .setPackageName("java.lang")
                    .build(false, Primitives.PRIMITIVES.objectTypeInfo));
        }
        TypeInfo someType = new TypeInfo("some.type");
        someType.typeAnalysis.set(new TypeAnalysis(someType));
        MethodInfo methodInfo = new MethodInfo(someType, List.of());
        ParameterInfo pi = new ParameterInfo(methodInfo, Primitives.PRIMITIVES.stringParameterizedType, name, 0);
        pi.parameterInspection.set(new ParameterInspection.ParameterInspectionBuilder().build());
        pi.parameterAnalysis.set(new ParameterAnalysis(pi));
        methodInfo.methodInspection.set(new MethodInspection.MethodInspectionBuilder()
                .addParameter(pi)
                .build(methodInfo));
        someType.typeInspection.set(new TypeInspection.TypeInspectionBuilder()
                .setPackageName("org.e2immu.test")
                .addMethod(methodInfo)
                .build(true, someType));
        methodInfo.methodAnalysis.set(new MethodAnalysis(methodInfo));
        return pi;
    }

    static EvaluationContext minimalEvaluationContext = new EvaluationContext() {

        @Override
        public Value currentValue(Variable variable) {
            return new VariableValue(this, variable, variable.name());
        }

        @Override
        public boolean equals(Variable variable, Variable other) {
            return variable.name().equals(other.name());
        }

    };

    static final Variable va = createVariable("a");
    static final Variable vb = createVariable("b");
    static final Variable vc = createVariable("c");
    static final Variable vd = createVariable("d");
    static final VariableValue a = new VariableValue(minimalEvaluationContext, va, "a");
    static final VariableValue b = new VariableValue(minimalEvaluationContext, vb, "b");
    static final VariableValue c = new VariableValue(minimalEvaluationContext, vc, "c");
    static final VariableValue d = new VariableValue(minimalEvaluationContext, vd, "d");

    static final Variable vi = createVariable("i");
    static final Variable vj = createVariable("j");
    static final Variable vk = createVariable("k");
    static final VariableValue i = new VariableValue(minimalEvaluationContext, vi, "i");
    static final VariableValuePlaceholder iph = new VariableValuePlaceholder(i, i, minimalEvaluationContext, ObjectFlow.NO_FLOW);
    static final VariableValue j = new VariableValue(minimalEvaluationContext, vj, "j");
    static final VariableValue k = new VariableValue(minimalEvaluationContext, vk, "k");

    static final Variable vs = createVariable("s");
    static final Variable vt = createVariable("t");
    static final VariableValue s = new VariableValue(minimalEvaluationContext, vs, "s");
    static final VariableValue t = new VariableValue(minimalEvaluationContext, vt, "t");

    static final Variable vp = createParameter("p");
    static final VariableValue p = new VariableValue(minimalEvaluationContext, vp, "p");

}
