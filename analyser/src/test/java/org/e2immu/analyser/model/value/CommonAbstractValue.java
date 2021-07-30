/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.util.DelayDebugNode;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.QualifiedName;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.Resources;
import org.junit.jupiter.api.BeforeAll;

import java.util.Set;
import java.util.stream.Stream;

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

    protected static Variable vq;
    protected static VariableExpression q;

    @BeforeAll
    public static void beforeClass() {
        Logger.activate(Logger.LogTarget.EXPRESSION);

        TYPE_MAP_BUILDER = new TypeMapImpl.Builder(new Resources());
        PRIMITIVES = TYPE_MAP_BUILDER.getPrimitives();
        PRIMITIVES.setInspectionOfBoxedTypesForTesting();

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

        vq = createVariable("q"); // not intercepted
        q = new VariableExpression(vq);
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
        return And.and(minimalEvaluationContext, values);
    }

    protected static Expression newOrAppend(Expression... values) {
        return Or.or(minimalEvaluationContext, values);
    }

    protected static Expression negate(Expression value) {
        return Negation.negate(minimalEvaluationContext, value);
    }

    protected static Expression newInt(int i) {
        return new IntConstant(PRIMITIVES, i);
    }

    static ParameterInfo createParameter() {
        assert PRIMITIVES != null;
        if (!PRIMITIVES.objectTypeInfo.typeInspection.isSet()) {
            PRIMITIVES.objectTypeInfo.typeInspection.set(new TypeInspectionImpl.Builder(PRIMITIVES.objectTypeInfo, TypeInspectionImpl.InspectionState.BY_HAND)
                    .noParent(PRIMITIVES)
                    .build());
        }
        TypeInfo someType = new TypeInfo("some", "type");
        someType.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                PRIMITIVES, someType, null).build());
        MethodInspectionImpl.Builder methodBuilder = new MethodInspectionImpl.Builder(someType, "type");
        ParameterInspectionImpl.Builder pi = new ParameterInspectionImpl.Builder(Identifier.generate(),
                PRIMITIVES.stringParameterizedType, "p", 0);
        methodBuilder.setReturnType(PRIMITIVES.stringParameterizedType).addParameter(pi);
        MethodInfo methodInfo = methodBuilder.build(InspectionProvider.DEFAULT).getMethodInfo();
        ParameterInfo p0 = methodInfo.methodInspection.get().getParameters().get(0);
        p0.setAnalysis(new ParameterAnalysisImpl.Builder(PRIMITIVES, null, p0));

        someType.typeInspection.set(new TypeInspectionImpl.Builder(someType, TypeInspectionImpl.InspectionState.BY_HAND)
                .noParent(PRIMITIVES)
                .addMethod(methodInfo)
                .build());
        return p0;
    }

    protected final static AnalyserContext analyserContext = () -> PRIMITIVES;

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
            return new EvaluationContextImpl(conditionManager
                    .newAtStartOfNewBlockDoNotChangePrecondition(PRIMITIVES, condition, null));
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime, ForwardEvaluationInfo forwardEvaluationInfo) {
            return new VariableExpression(variable);
        }

        @Override
        public Primitives getPrimitives() {
            return PRIMITIVES;
        }

        @Override
        public int getProperty(Expression value,
                               VariableProperty variableProperty,
                               boolean duringEvaluation,
                               boolean ignoreStateInConditionManager) {
            if (value instanceof VariableExpression ve && variableProperty == VariableProperty.NOT_NULL_EXPRESSION
                    && !"q".equals(ve.variable().simpleName())) {
                if (ve.variable().simpleName().endsWith("n") || ve.variable().simpleName().compareTo("p") >= 0)
                    return MultiLevel.NULLABLE;
                return MultiLevel.EFFECTIVELY_NOT_NULL;
            }
            return value.getProperty(minimalEvaluationContext, variableProperty, true);
        }

        @Override
        public TypeInfo getCurrentType() {
            return PRIMITIVES.booleanTypeInfo;
        }

        @Override
        public Stream<DelayDebugNode> streamNodes() {
            throw new UnsupportedOperationException();
        }
    }

    protected static Expression equals(Expression v1, Expression v2) {
        return Equals.equals(minimalEvaluationContext, v1, v2);
    }
}
