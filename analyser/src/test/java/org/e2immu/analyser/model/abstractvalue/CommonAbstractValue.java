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

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.ConditionManager;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.StringValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.junit.BeforeClass;

import java.util.List;
import java.util.Set;

public abstract class CommonAbstractValue {

    protected static Primitives PRIMITIVES;
    protected static BoolValue TRUE;
    protected static BoolValue FALSE;

    protected static Variable va;
    protected static Variable vb;
    protected static Variable vc;
    protected static Variable vd;
    protected static VariableValue a;
    protected static VariableValue b;
    protected static VariableValue c;
    protected static VariableValue d;

    protected static Variable vi;
    protected static Variable vj;
    protected static VariableValue i;
    protected static VariableValue j;

    protected static Variable vs;
    protected static VariableValue s;

    protected static Variable vp;
    protected static VariableValue p;

    @BeforeClass
    public static void beforeClass() {
        PRIMITIVES = new Primitives();
        TRUE = new BoolValue(PRIMITIVES, true);
        FALSE = new BoolValue(PRIMITIVES, false);
        Logger.activate(Logger.LogTarget.CNF);

        va = createVariable("a");
        vb = createVariable("b");
        vc = createVariable("c");
        vd = createVariable("d");
        a = new VariableValue(va);
        b = new VariableValue(vb);
        c = new VariableValue(vc);
        d = new VariableValue(vd);

        vi = createVariable("i");
        vj = createVariable("j");
        i = new VariableValue(vi);
        j = new VariableValue(vj);

        vs = createVariable("s");
        s = new VariableValue(vs);

        vp = createParameter("p");
        p = new VariableValue(vp);
    }

    static Variable createVariable(String name) {
        return new Variable() {
            @Override
            public ParameterizedType parameterizedType() {
                if (Set.of("a", "b", "c", "d").contains(name)) return PRIMITIVES.booleanParameterizedType;
                if (Set.of("i", "j", "k").contains(name)) return PRIMITIVES.intParameterizedType;
                if (Set.of("s", "t", "p").contains(name)) return PRIMITIVES.stringParameterizedType;
                return null;
            }

            @Override
            public ParameterizedType concreteReturnType() {
                return parameterizedType();
            }

            @Override
            public String simpleName() {
                return name;
            }

            @Override
            public String fullyQualifiedName() {
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
        };
    }

    protected static Value newAndAppend(Value... values) {
        return new AndValue(PRIMITIVES).append(minimalEvaluationContext, values);
    }

    protected static Value newOrAppend(Value... values) {
        return new OrValue(PRIMITIVES).append(minimalEvaluationContext, values);
    }

    protected static Value negate(Value value) {
        return NegatedValue.negate(minimalEvaluationContext, value);
    }

    protected static Value newInt(int i) {
        return new IntValue(PRIMITIVES, i, ObjectFlow.NO_FLOW);
    }
    protected static Value newString(String s) {
        return new StringValue(PRIMITIVES, s, ObjectFlow.NO_FLOW);
    }

    static ParameterInfo createParameter(String name) {
        assert PRIMITIVES != null;
        if (!PRIMITIVES.objectTypeInfo.typeInspection.isSetPotentiallyRun()) {
            PRIMITIVES.objectTypeInfo.typeInspection.set(new TypeInspection.TypeInspectionBuilder()
                    .setPackageName("java.lang")
                    .build(false, PRIMITIVES.objectTypeInfo));
        }
        TypeInfo someType = new TypeInfo("some.type");
        someType.typeAnalysis.set(new TypeAnalysisImpl.Builder(PRIMITIVES, someType).build());
        MethodInfo methodInfo = new MethodInfo(someType, List.of());
        ParameterInfo pi = new ParameterInfo(methodInfo, PRIMITIVES.stringParameterizedType, name, 0);
        pi.parameterInspection.set(new ParameterInspection.ParameterInspectionBuilder().build());
        pi.setAnalysis(new ParameterAnalysisImpl.Builder(PRIMITIVES, null, pi));
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

    protected final static AnalyserContext analyserContext = new AnalyserContext() {
    };

    protected final static EvaluationContext minimalEvaluationContext = new EvaluationContext() {
        @Override
        public ConditionManager getConditionManager() {
            return ConditionManager.INITIAL;
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Value currentValue(Variable variable) {
            return new VariableValue(variable);
        }

        @Override
        public boolean isNotNull0(Value value) {
            return false; // no opinion
        }

        @Override
        public Primitives getPrimitives() {
            return PRIMITIVES;
        }
    };

    protected static Value equals(Value v1, Value v2) {
        return EqualsValue.equals(minimalEvaluationContext, v1, v2, ObjectFlow.NO_FLOW);
    }
}
