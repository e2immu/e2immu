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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analyser.util.ConditionManagerImpl;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.expr.ParseArrayCreationExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.e2immu.analyser.parser.impl.TypeMapImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestAssignment {

    private final Primitives primitives = new PrimitivesImpl();
    private final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);
    private final ForwardEvaluationInfo onlySort = new ForwardEvaluationInfo.Builder().setOnlySort(true).build();

    private final AnalyserContext analyserContext = new AnalyserContext() {
        @Override
        public Primitives getPrimitives() {
            return primitives;
        }
    };

    private final TypeMap typeMap = new TypeMap() {
        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        @Override
        public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
            return inspectionProvider.getFieldInspection(fieldInfo);
        }

        @Override
        public MethodInspection getMethodInspection(MethodInfo methodInfo) {
            return inspectionProvider.getMethodInspection(methodInfo);
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
            return inspectionProvider.getTypeInspection(typeInfo);
        }
    };

    private final TypeContext typeContext = new TypeContext() {
        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        @Override
        public TypeMap typeMap() {
            return typeMap;
        }
    };

    private EvaluationContext evaluationContext(Map<String, Expression> variableValues) {
        return new AbstractEvaluationContextImpl() {
            @Override
            public DV getProperty(Expression value, Property property,
                                  boolean duringEvaluation, boolean ignoreStateInConditionManager) {
                return null;
            }

            @Override
            public int getDepth() {
                return 0;
            }

            @Override
            public Expression currentValue(Variable variable,
                                           Expression scopeValue,
                                           Expression indexValue,
                                           Identifier identifier, ForwardEvaluationInfo forwardEvaluationInfo) {
                return Objects.requireNonNull(variableValues.get(variable.simpleName()));
            }

            @Override
            public AnalyserContext getAnalyserContext() {
                return analyserContext;
            }

            @Override
            public ConditionManager getConditionManager() {
                return ConditionManagerImpl.initialConditionManager(primitives);
            }

            @Override
            public TypeInfo getCurrentType() {
                return primitives.stringTypeInfo();
            }

            @Override
            public Location getLocation(Stage level) {
                return Location.NOT_YET_SET;
            }
        };
    }

    private EvaluationResult context(EvaluationContext evaluationContext) {
        return new EvaluationResultImpl.Builder(evaluationContext).build();
    }

    private LocalVariable makeLocalVariableInt(String name) {
        return new LocalVariable.Builder()
                .setName(name)
                .setParameterizedType(primitives.intParameterizedType())
                .setOwningType(primitives.stringTypeInfo())
                .build();
    }

    private VariableExpression makeLVAsExpression(String name, Expression initializer) {
        LocalVariable lvi = makeLocalVariableInt(name);
        LocalVariableCreation i = new LocalVariableCreation(newId(), newId(),
                new LocalVariableReference(lvi, initializer));
        return new VariableExpression(newId(), i.localVariableReference);
    }

    private static Identifier newId() {
        return Identifier.generate("test");
    }

    @Test
    @DisplayName("int i=0; i+=1;")
    public void test1() {
        LocalVariable lvi = makeLocalVariableInt("i");
        IntConstant zero = IntConstant.zero(primitives);
        LocalVariableCreation i = new LocalVariableCreation(newId(), newId(),
                new LocalVariableReference(lvi, zero));
        assertEquals("int i=0", i.minimalOutput());
        MethodInfo plusEquals = primitives.assignPlusOperatorInt();
        assertNotNull(plusEquals);
        assertEquals("int.+=(int)", plusEquals.fullyQualifiedName());
        Assignment iPlusEquals1 = new Assignment(newId(), primitives,
                new VariableExpression(newId(), i.localVariableReference), IntConstant.one(primitives),
                plusEquals, null, false,
                false, null);
        assertEquals("i+=1", iPlusEquals1.minimalOutput());
        EvaluationResult context = context(evaluationContext(Map.of("i", zero)));
        EvaluationResult result = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("1", result.value().toString());
    }

    @Test
    @DisplayName("i+=1, ++i, i++")
    public void test2() {
        IntConstant zero = IntConstant.zero(primitives);
        VariableExpression ve = makeLVAsExpression("i", zero);
        IntConstant one = IntConstant.one(primitives);
        Expression iPlusEquals1 = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), null,
                true, true, null);
        assertEquals("i+=1", iPlusEquals1.minimalOutput());
        EvaluationResult context = context(evaluationContext(Map.of("i", zero)));
        EvaluationResult result = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("1", result.value().toString());

        Expression plusPlusI = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), true,
                true, true, null);
        assertEquals("++i", plusPlusI.minimalOutput());
        EvaluationResult result2 = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("1", result2.value().toString());

        Expression iPlusPlus = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), false,
                true, true, null);
        assertEquals("i++", iPlusPlus.minimalOutput());
        EvaluationResult result3 = iPlusPlus.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("0", result3.value().toString());
    }

    @Test
    @DisplayName("i++ ++i")
    public void test3() {
        VariableExpression ve = makeLVAsExpression("i", IntConstant.zero(primitives));

        Expression iPlusPlus = new UnaryOperator(newId(), primitives.postfixIncrementOperatorInt(),
                ve, Precedence.PLUSPLUS);
        assertEquals("i++", iPlusPlus.minimalOutput());

        Expression plusPlusI = new UnaryOperator(newId(), primitives.prefixIncrementOperatorInt(),
                ve, Precedence.UNARY);
        assertEquals("++i", plusPlusI.minimalOutput());
    }

    @Test
    @DisplayName("direct assignment i=j")
    public void test4() {
        IntConstant zero = IntConstant.zero(primitives);
        VariableExpression vi = makeLVAsExpression("i", zero);
        Instance instance = Instance.forTesting(primitives.intParameterizedType());
        VariableExpression vj = makeLVAsExpression("j", instance);
        Assignment assignment = new Assignment(primitives, vi, vj);
        assertEquals("i=j", assignment.minimalOutput());
        EvaluationResult context = context(evaluationContext(Map.of("i", zero, "j", instance)));
        EvaluationResult result = assignment.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("j:0", result.linkedVariables(vi.variable()).toString());
    }

    @Test
    @DisplayName("only sort")
    public void test5() {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression ve = makeLVAsExpression("i", three);
        IntConstant one = IntConstant.one(primitives);
        Expression iPlusEquals1 = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), null,
                true, true, null);
        EvaluationResult context = context(evaluationContext(Map.of("i", three)));
        EvaluationResult onlySortResult = iPlusEquals1.evaluate(context, onlySort);
        assertEquals("i+=1", onlySortResult.value().toString());
        EvaluationResult eval = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("4", eval.value().toString());
    }

    @Test
    @DisplayName("assignment to self")
    public void test6() {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression ve = makeLVAsExpression("i", three);
        Assignment toSelf = new Assignment(newId(), primitives, ve, ve);
        EvaluationResult context = context(evaluationContext(Map.of("i", three)));
        EvaluationResult er = toSelf.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals(1, er.messages().size());
    }

    @Test
    @DisplayName("evaluationOfValue")
    public void test7() {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression ve = makeLVAsExpression("i", three);
        IntConstant five = new IntConstant(primitives, 5);
        EvaluationResult context = context(evaluationContext(Map.of("i", three)));
        EvaluationResult evalFour = five.evaluate(context, ForwardEvaluationInfo.DEFAULT);

        IntConstant one = IntConstant.one(primitives);
        Assignment iPlusEquals1 = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), null,
                true, true, evalFour);
        EvaluationResult onlySortResult = iPlusEquals1.evaluate(context, onlySort);
        assertEquals("i+=1", onlySortResult.value().toString());
        EvaluationResult eval = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        // start off with 3, add 5 instead of 1
        assertEquals("8", eval.value().toString());
    }

    @Test
    @DisplayName("assign to array: int[] a = new int[10]; a[i]=j")
    public void test8() {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression ve = makeLVAsExpression("i", three);
        Instance instance = Instance.forTesting(primitives.intParameterizedType());
        VariableExpression vj = makeLVAsExpression("j", instance);
        ParameterizedType intArray = new ParameterizedType(primitives.intTypeInfo(), 1);
        MethodInfo constructor = ParseArrayCreationExpr.createArrayCreationConstructor(typeContext, intArray);
        Expression newIntArray = new ConstructorCall(newId(), null, constructor, intArray,
                Diamond.NO, List.of(new IntConstant(primitives, 10)), null, null);
        LocalVariable aLv = new LocalVariable.Builder()
                .setName("a")
                .setParameterizedType(intArray)
                .setOwningType(primitives.stringTypeInfo())
                .build();
        LocalVariableCreation a = new LocalVariableCreation(newId(), newId(),
                new LocalVariableReference(aLv, newIntArray));
        assertEquals("int[] a=new int[10]", a.minimalOutput());
        VariableExpression va = new VariableExpression(newId(), a.localVariableReference);
        DependentVariable aiDv = new DependentVariable(newId(), va, va.variable(), ve, ve.variable(),
                intArray, "0");
        VariableExpression ai = new VariableExpression(newId(), aiDv);
        Assignment assignment = new Assignment(primitives, ai, vj);
        assertEquals("a[i]=j", assignment.minimalOutput());
    }
}
