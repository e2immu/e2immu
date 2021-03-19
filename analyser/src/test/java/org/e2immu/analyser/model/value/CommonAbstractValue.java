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

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.QualifiedName;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;

import java.util.Set;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.BY_HAND;

public abstract class CommonAbstractValue {
    protected static TypeMapImpl.Builder TYPE_MAP_BUILDER;
    protected static Primitives PRIMITIVES;
    protected static BooleanConstant TRUE;
    protected static BooleanConstant FALSE;

    protected static Variable va;
    protected static Variable vb;
    protected static Variable van;
    protected static Variable vbn;
    protected static Variable vc;
    protected static Variable vd;
    protected static VariableExpression a;
    protected static VariableExpression b;
    protected static VariableExpression an; // nullable
    protected static VariableExpression bn; // nullable
    protected static VariableExpression c;
    protected static VariableExpression d;

    protected static Variable vi;
    protected static Variable vj;
    protected static VariableExpression i;
    protected static VariableExpression j;

    protected static Variable vs;
    protected static VariableExpression s;

    protected static Variable vp;
    protected static VariableExpression p;

    @BeforeAll
    public static void beforeClass() {
        Logger.activate(Logger.LogTarget.CNF);

        TYPE_MAP_BUILDER = new TypeMapImpl.Builder();
        PRIMITIVES = TYPE_MAP_BUILDER.getPrimitives();
        TRUE = new BooleanConstant(PRIMITIVES, true);
        FALSE = new BooleanConstant(PRIMITIVES, false);
        minimalEvaluationContext = new EvaluationContextImpl();
        va = createVariable("a");
        vb = createVariable("b");
        vc = createVariable("c");
        vd = createVariable("d");
        a = new VariableExpression(va);
        b = new VariableExpression(vb);
        c = new VariableExpression(vc);
        d = new VariableExpression(vd);
        van = createVariable("an");
        vbn = createVariable("bn");
        an = new VariableExpression(van);
        bn = new VariableExpression(vbn);

        vi = createVariable("i");
        vj = createVariable("j");
        i = new VariableExpression(vi);
        j = new VariableExpression(vj);

        vs = createVariable("s"); // nullable
        s = new VariableExpression(vs);

        vp = createParameter(); // nullable
        p = new VariableExpression(vp);
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
            public OutputBuilder output(Qualification qualification) {
                return new OutputBuilder().add(new QualifiedName(name));
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    protected static Expression newAndAppend(Expression... values) {
        return new And(PRIMITIVES).append(minimalEvaluationContext, values);
    }

    protected static Expression newOrAppend(Expression... values) {
        return new Or(PRIMITIVES).append(minimalEvaluationContext, values);
    }

    protected static Expression negate(Expression value) {
        return Negation.negate(minimalEvaluationContext, value);
    }

    protected static Expression newInt(int i) {
        return new IntConstant(PRIMITIVES, i, ObjectFlow.NO_FLOW);
    }

    static ParameterInfo createParameter() {
        assert PRIMITIVES != null;
        if (!PRIMITIVES.objectTypeInfo.typeInspection.isSet()) {
            PRIMITIVES.objectTypeInfo.typeInspection.set(new TypeInspectionImpl.Builder(PRIMITIVES.objectTypeInfo, BY_HAND)
                    .setParentClass(PRIMITIVES.objectParameterizedType)
                    .build());
        }
        TypeInfo someType = new TypeInfo("some", "type");
        someType.typeAnalysis.set(new TypeAnalysisImpl.Builder(PRIMITIVES, someType).build());
        MethodInspectionImpl.Builder methodBuilder = new MethodInspectionImpl.Builder(someType, "type");
        ParameterInspectionImpl.Builder pi = new ParameterInspectionImpl.Builder(PRIMITIVES.stringParameterizedType, "p", 0);
        methodBuilder.setReturnType(PRIMITIVES.stringParameterizedType).addParameter(pi);
        MethodInfo methodInfo = methodBuilder.build(InspectionProvider.DEFAULT).getMethodInfo();
        ParameterInfo p0 = methodInfo.methodInspection.get().getParameters().get(0);
        p0.setAnalysis(new ParameterAnalysisImpl.Builder(PRIMITIVES, null, p0));

        someType.typeInspection.set(new TypeInspectionImpl.Builder(someType, BY_HAND)
                .setParentClass(PRIMITIVES.objectParameterizedType)
                .addMethod(methodInfo)
                .build());
        return p0;
    }

    protected final static AnalyserContext analyserContext = new AnalyserContext() {
    };

    protected static EvaluationContext minimalEvaluationContext;

    static class EvaluationContextImpl extends AbstractEvaluationContextImpl {

        private EvaluationContextImpl(ConditionManager conditionManager) {
            super(0, conditionManager, null);
        }

        EvaluationContextImpl() {
            super(0, ConditionManager.initialConditionManager(PRIMITIVES), null);
        }

        @Override
        public EvaluationContext child(Expression condition) {
            return new EvaluationContextImpl(conditionManager.newAtStartOfNewBlockDoNotChangePrecondition(PRIMITIVES, condition, false));
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
            return new VariableExpression(variable);
        }

        @Override
        public Primitives getPrimitives() {
            return PRIMITIVES;
        }

        @Override
        public int getProperty(Expression value, VariableProperty variableProperty, boolean duringEvaluation) {
            if (value instanceof VariableExpression ve && variableProperty == VariableProperty.NOT_NULL_EXPRESSION) {
                if (ve.variable().simpleName().endsWith("n") || ve.variable().simpleName().compareTo("p") >= 0)
                    return MultiLevel.NULLABLE;
                return MultiLevel.EFFECTIVELY_NOT_NULL;
            }
            return value.getProperty(minimalEvaluationContext, variableProperty, true);
        }

        @Override
        public String newObjectIdentifier() {
            return "-";
        }
    }

    protected static Expression equals(Expression v1, Expression v2) {
        return Equals.equals(minimalEvaluationContext, v1, v2, ObjectFlow.NO_FLOW);
    }
}
