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
        //methodInfo.methodAnalysis.set(new MethodAnalysis(methodInfo));
        return pi;
    }

    protected final static EvaluationContext minimalEvaluationContext = new EvaluationContext() {

        @Override
        public Value currentValue(Variable variable) {
            return new VariableValue(variable);
        }

        @Override
        public boolean isNotNull0(Value value) {
            return false; // no opinion
        }
    };

    protected static Value equals(Value v1, Value v2) {
        return EqualsValue.equals(v1, v2, ObjectFlow.NO_FLOW, minimalEvaluationContext);
    }

    static final Variable va = createVariable("a");
    static final Variable vb = createVariable("b");
    static final Variable vc = createVariable("c");
    static final Variable vd = createVariable("d");
    static final VariableValue a = new VariableValue(va);
    static final VariableValue b = new VariableValue(vb);
    static final VariableValue c = new VariableValue(vc);
    static final VariableValue d = new VariableValue(vd);

    static final Variable vi = createVariable("i");
    static final Variable vj = createVariable("j");
    static final Variable vk = createVariable("k");
    static final VariableValue i = new VariableValue(vi);
    static final Value iPropertyWrapper = PropertyWrapper.propertyWrapper(new VariableValue(vi),
            Map.of(), ObjectFlow.NO_FLOW);
    static final VariableValue j = new VariableValue(vj);
    static final VariableValue k = new VariableValue(vk);

    static final Variable vs = createVariable("s");
    static final Variable vt = createVariable("t");
    static final VariableValue s = new VariableValue(vs);
    static final VariableValue t = new VariableValue(vt);

    static final Variable vp = createParameter("p");
    static final VariableValue p = new VariableValue(vp);

}
