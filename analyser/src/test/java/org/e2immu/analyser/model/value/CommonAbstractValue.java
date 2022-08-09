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
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.QualifiedName;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.TypeMapImpl;
import org.e2immu.analyser.util.Resources;
import org.junit.jupiter.api.BeforeAll;

import java.util.Arrays;
import java.util.Set;

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
    protected static VariableExpression s, s1, s2, s3, s4, s5, s6;

    protected static Variable vp;
    protected static VariableExpression p;
    protected static DelayedVariableExpression delayedP;

    protected static Variable vq;
    protected static VariableExpression q;

    @BeforeAll
    public static void beforeClass() {
        TYPE_MAP_BUILDER = new TypeMapImpl.Builder(new Resources());
        PRIMITIVES = TYPE_MAP_BUILDER.getPrimitives();
        PRIMITIVES.setInspectionOfBoxedTypesForTesting();

        TRUE = new BooleanConstant(PRIMITIVES, true);
        FALSE = new BooleanConstant(PRIMITIVES, false);
        context = EvaluationResult.from(new EvaluationContextImpl());
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
        s1 = new VariableExpression(createVariable("s1"));
        s2 = new VariableExpression(createVariable("s2"));
        s3 = new VariableExpression(createVariable("s3"));
        s4 = new VariableExpression(createVariable("s4"));
        s5 = new VariableExpression(createVariable("s5"));
        s6 = new VariableExpression(createVariable("s6"));

        vp = createParameter(); // nullable
        p = new VariableExpression(vp);
        delayedP = DelayedVariableExpression.forParameter((ParameterInfo) vp,
                DelayFactory.createDelay(new SimpleCause(Location.NOT_YET_SET, CauseOfDelay.Cause.NOT_INVOLVED)));
        vq = createVariable("q"); // not intercepted
        q = new VariableExpression(vq);
    }

    static Variable createVariable(String name) {
        return new Variable() {
            @Override
            public ParameterizedType parameterizedType() {
                if (Set.of("a", "b", "c", "d").contains(name)) return PRIMITIVES.booleanParameterizedType();
                if (Set.of("i", "j", "k").contains(name)) return PRIMITIVES.intParameterizedType();
                if (Set.of("s", "t", "p").contains(name)) return PRIMITIVES.stringParameterizedType();
                return PRIMITIVES.intParameterizedType(); // to have something
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
        return And.and(context, values);
    }

    protected static Expression newAnd(Expression... values) {
        return new And(PRIMITIVES, Arrays.stream(values).toList());
    }

    protected static Expression newOrAppend(Expression... values) {
        return Or.or(context, values);
    }

    protected static Expression newOr(Expression... values) {
        return new Or(Identifier.constant("or"), PRIMITIVES, Arrays.stream(values).toList());
    }

    protected static Expression negate(Expression value) {
        return Negation.negate(context, value);
    }

    protected static Expression newInt(int i) {
        return new IntConstant(PRIMITIVES, i);
    }

    protected static InlineConditional newInline(Expression c, Expression l, Expression r) {
        return new InlineConditional(Identifier.constant("newInline"), context.getAnalyserContext(),
                c, l, r);
    }

    protected static Expression newEquals(Expression l, Expression r) {
        return new Equals(Identifier.constant("newEquals"), PRIMITIVES, l, r);
    }

    static ParameterInfo createParameter() {
        assert PRIMITIVES != null;
        if (!PRIMITIVES.objectTypeInfo().typeInspection.isSet()) {
            PRIMITIVES.objectTypeInfo().typeInspection.set(new TypeInspectionImpl.Builder(PRIMITIVES.objectTypeInfo(), InspectionState.BY_HAND)
                    .noParent(PRIMITIVES)
                    .setAccess(Inspection.Access.PUBLIC)
                    .build(null));
        }
        TypeInfo someType = new TypeInfo("some", "type");
        someType.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                PRIMITIVES, someType, null).build());
        MethodInspectionImpl.Builder methodBuilder = new MethodInspectionImpl.Builder(someType, "type");
        ParameterInspectionImpl.Builder pi = new ParameterInspectionImpl.Builder(Identifier.generate("test"),
                PRIMITIVES.stringParameterizedType(), "p", 0);
        methodBuilder.setReturnType(PRIMITIVES.stringParameterizedType())
                .setAccess(Inspection.Access.PUBLIC)
                .addParameter(pi);
        MethodInfo methodInfo = methodBuilder.build(InspectionProvider.DEFAULT).getMethodInfo();
        ParameterInfo p0 = methodInfo.methodInspection.get().getParameters().get(0);
        p0.setAnalysis(new ParameterAnalysisImpl.Builder(PRIMITIVES, null, p0));

        someType.typeInspection.set(new TypeInspectionImpl.Builder(someType, InspectionState.BY_HAND)
                .noParent(PRIMITIVES)
                .addMethod(methodInfo)
                .setAccess(Inspection.Access.PUBLIC)
                .build(null));
        return p0;
    }

    protected final static AnalyserContext analyserContext = () -> PRIMITIVES;

    protected static EvaluationResult context;

    static class EvaluationContextImpl extends AbstractEvaluationContextImpl {

        private EvaluationContextImpl(ConditionManager conditionManager) {
            super(1, 0, false, conditionManager, null);
        }

        EvaluationContextImpl() {
            super(1, 0, false,
                    ConditionManager.initialConditionManager(PRIMITIVES), null);
        }

        @Override
        public EvaluationContext child(Expression condition, Set<Variable> conditionVariables) {
            return new EvaluationContextImpl(conditionManager
                    .newAtStartOfNewBlockDoNotChangePrecondition(PRIMITIVES, condition, conditionVariables));
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Expression currentValue(Variable variable,
                                       Expression scopeValue,
                                       Expression indexValue,
                                       ForwardEvaluationInfo forwardEvaluationInfo) {
            return new VariableExpression(variable, VariableExpression.NO_SUFFIX, scopeValue, null);
        }

        @Override
        public Primitives getPrimitives() {
            return PRIMITIVES;
        }

        @Override
        public DV getProperty(Expression value,
                              Property property,
                              boolean duringEvaluation,
                              boolean ignoreStateInConditionManager) {
            if (value instanceof VariableExpression ve && property == Property.NOT_NULL_EXPRESSION
                    && !"q".equals(ve.variable().simpleName())) {
                if (ve.variable().simpleName().endsWith("n") || ve.variable().simpleName().compareTo("p") >= 0)
                    return MultiLevel.NULLABLE_DV;
                return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            }
            return value.getProperty(context, property, true);
        }

        @Override
        public TypeInfo getCurrentType() {
            return PRIMITIVES.booleanTypeInfo();
        }
    }

    protected static Expression equals(Expression v1, Expression v2) {
        return Equals.equals(context, v1, v2);
    }
}
