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
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analyser.nonanalyserimpl.CommonEvaluationContext;
import org.e2immu.analyser.analyser.ConditionManager;
import org.e2immu.analyser.analyser.util.ConditionManagerImpl;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.LocalVariableReference;
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

import static org.e2immu.analyser.model.Inspector.BY_HAND;

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

    protected static Variable vin;
    protected static VariableExpression vine;

    protected static Variable vl, vm;
    protected static VariableExpression l, m;

    protected static Variable vs;
    protected static VariableExpression s, s1, s2, s3, s4, s5, s6;

    protected static Variable vp;
    protected static VariableExpression p;
    protected static DelayedVariableExpression delayedP;

    protected static Variable vq;
    protected static VariableExpression q;

    @BeforeAll
    public static void beforeClass() {
        TYPE_MAP_BUILDER = new TypeMapImpl.Builder(new Resources(), false);
        PRIMITIVES = TYPE_MAP_BUILDER.getPrimitives();
        PRIMITIVES.setInspectionOfBoxedTypesForTesting();

        TRUE = new BooleanConstant(PRIMITIVES, true);
        FALSE = new BooleanConstant(PRIMITIVES, false);
        context = EvaluationResultImpl.from(new EvaluationContextImpl());
        va = createVariable("a");
        vb = createVariable("b");
        vc = createVariable("c");
        vd = createVariable("d");
        a = newVariableExpression(va);
        b = newVariableExpression(vb);
        c = newVariableExpression(vc);
        d = newVariableExpression(vd);
        van = createVariable("an");
        vbn = createVariable("bn");
        an = newVariableExpression(van);
        bn = newVariableExpression(vbn);

        vin = createVariable("in"); // nullable
        vine = newVariableExpression(vin);

        vi = createVariable("i");
        vj = createVariable("j");
        i = newVariableExpression(vi);
        j = newVariableExpression(vj);

        vl = createVariable("l");
        vm = createVariable("m");
        l = newVariableExpression(vl);
        m = newVariableExpression(vm);

        vs = createVariable("s"); // nullable
        s = newVariableExpression(vs);
        s1 = newVariableExpression(createVariable("s1"));
        s2 = newVariableExpression(createVariable("s2"));
        s3 = newVariableExpression(createVariable("s3"));
        s4 = newVariableExpression(createVariable("s4"));
        s5 = newVariableExpression(createVariable("s5"));
        s6 = newVariableExpression(createVariable("s6"));

        vp = createParameter(); // nullable
        p = newVariableExpression(vp);
        delayedP = DelayedVariableExpression.forParameter((ParameterInfo) vp,
                DelayFactory.createDelay(new SimpleCause(Location.NOT_YET_SET, CauseOfDelay.Cause.NOT_INVOLVED)));
        vq = createVariable("q"); // not intercepted
        q = newVariableExpression(vq);
    }

    static VariableExpression newVariableExpression(Variable vd) {
        return new VariableExpression(Identifier.CONSTANT, vd);
    }

    static Variable createVariable(String name) {
        return new Variable() {
            @Override
            public ParameterizedType parameterizedType() {
                if (Set.of("a", "b", "c", "d").contains(name)) return PRIMITIVES.booleanParameterizedType();
                if (Set.of("i", "j", "k").contains(name)) return PRIMITIVES.intParameterizedType();
                if (Set.of("l", "m").contains(name)) return PRIMITIVES.doubleParameterizedType();
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

            @Override
            public int getComplexity() {
                return LocalVariableReference.COMPLEXITY;
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

    protected static Expression sum(Expression l, Expression r) {
        return Sum.sum(Identifier.CONSTANT, context, l, r);
    }

    protected static Expression product(Expression l, Expression r) {
        return Product.product(Identifier.CONSTANT, context, l, r);
    }

    static ParameterInfo createParameter() {
        assert PRIMITIVES != null;
        if (!PRIMITIVES.objectTypeInfo().typeInspection.isSet()) {
            PRIMITIVES.objectTypeInfo().typeInspection.set(new TypeInspectionImpl.Builder(PRIMITIVES.objectTypeInfo(), BY_HAND)
                    .noParent(PRIMITIVES)
                    .setAccess(Inspection.Access.PUBLIC)
                    .build(null));
        }
        TypeInfo someType = new TypeInfo("some", "type");
        someType.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                PRIMITIVES, someType, null).build());
        MethodInspectionImpl.Builder methodBuilder = new MethodInspectionImpl.Builder(someType, "type",
                MethodInfo.MethodType.METHOD);
        ParameterInspectionImpl.Builder pi = new ParameterInspectionImpl.Builder(Identifier.generate("test"),
                PRIMITIVES.stringParameterizedType(), "p", 0);
        methodBuilder.setReturnType(PRIMITIVES.stringParameterizedType())
                .setAccess(Inspection.Access.PUBLIC)
                .addParameter(pi);
        MethodInfo methodInfo = methodBuilder.build(InspectionProvider.DEFAULT).getMethodInfo();
        ParameterInfo p0 = methodInfo.methodInspection.get().getParameters().get(0);
        p0.setAnalysis(new ParameterAnalysisImpl.Builder(PRIMITIVES, null, p0));

        someType.typeInspection.set(new TypeInspectionImpl.Builder(someType, BY_HAND)
                .noParent(PRIMITIVES)
                .addMethod(methodInfo)
                .setAccess(Inspection.Access.PUBLIC)
                .build(null));
        return p0;
    }

    protected final static AnalyserContext analyserContext = new AnalyserContext() {
        @Override
        public Primitives getPrimitives() {
            return PRIMITIVES;
        }

        @Override
        public Configuration getConfiguration() {
            return new Configuration.Builder()
                    .setAnalyserConfiguration(new AnalyserConfiguration.Builder().setNormalizeMore(true).build())
                    .build();
        }
    };

    protected static EvaluationResult context;

    static class EvaluationContextImpl extends CommonEvaluationContext {

        private EvaluationContextImpl(ConditionManager conditionManager) {
            super(1, 0, BreakDelayLevel.NONE, conditionManager, null);
        }

        EvaluationContextImpl() {
            super(1, 0, BreakDelayLevel.NONE,
                    ConditionManagerImpl.initialConditionManager(PRIMITIVES), null);
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
                                       Identifier identifier,
                                       ForwardEvaluationInfo forwardEvaluationInfo) {
            return new VariableExpression(identifier, variable, VariableExpression.NO_SUFFIX, scopeValue, null);
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
