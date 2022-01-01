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
import org.e2immu.analyser.analyser.impl.AnnotatedAPIAnalyser;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.parser.InspectionProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestConditionalValue extends CommonAbstractValue {

    @Test
    public void test1() {
        Expression cv1 = inline(a, newInt(3), newInt(4));
        Expression cv2 = inline(a, newInt(3), newInt(4));
        assertEquals("a?3:4", cv1.toString());
        assertEquals("a?3:4", cv2.toString());
        assertEquals(cv1, cv2);
    }

    private static Expression inline(Expression c, Expression t, Expression f) {
        return EvaluateInlineConditional.conditionalValueConditionResolved(minimalEvaluationContext,
                c, t, f).value();
    }

    @Test
    public void test2() {
        Expression cv1 = inline(a, TRUE, b);
        assertEquals("a||b", cv1.toString());
        Expression cv2 = inline(a, FALSE, b);
        assertEquals("!a&&b", cv2.toString());
        Expression cv3 = inline(a, b, TRUE);
        assertEquals("!a||b", cv3.toString());
        Expression cv4 = inline(a, b, FALSE);
        assertEquals("a&&b", cv4.toString());
    }

    @Test
    public void test3() {
        TypeInfo annotatedAPI = new TypeInfo("org.e2immu.annotatedapi", "AnnotatedAPI");
        ParameterizedType annotatedAPIPt = new ParameterizedType(annotatedAPI, 0);
        MethodInfo isFact = new MethodInfo(Identifier.generate(),
                annotatedAPI, "isFact", AnnotatedAPIAnalyser.IS_FACT_FQN, AnnotatedAPIAnalyser.IS_FACT_FQN, false);
        isFact.methodInspection.set(new MethodInspectionImpl.Builder(annotatedAPI)
                .setStatic(true)
                .setReturnType(PRIMITIVES.booleanParameterizedType()).build(InspectionProvider.DEFAULT));
        Expression isFactA = new MethodCall(Identifier.generate(),
                new TypeExpression(annotatedAPIPt, Diamond.NO), isFact, List.of(a));
        assertEquals("AnnotatedAPI.isFact(a)", isFactA.toString());
        Expression isFactB = new MethodCall(Identifier.generate(),
                new TypeExpression(annotatedAPIPt, Diamond.NO), isFact, List.of(b));
        assertEquals("AnnotatedAPI.isFact(b)", isFactB.toString());

        assertTrue(minimalEvaluationContext.getConditionManager().state().isBoolValueTrue());
        Expression cv1 = inline(isFactA, a, b);
        assertSame(b, cv1);

        EvaluationContext child = minimalEvaluationContext.child(a);
        assertTrue(child.getConditionManager().state().isBoolValueTrue());
        assertEquals("a", child.getConditionManager().condition().toString());
        Expression cv2 = EvaluateInlineConditional.conditionalValueConditionResolved(child, isFactA, a, b).value();
        assertSame(a, cv2);

        EvaluationContext child2 = minimalEvaluationContext.child(And.and(minimalEvaluationContext, a, b));
        assertEquals("a&&b", child2.getConditionManager().condition().toString());
        assertTrue(child.getConditionManager().state().isBoolValueTrue());
        assertEquals("a&&b", child2.getConditionManager().absoluteState(child2).toString());

        Expression cv3 = EvaluateInlineConditional.conditionalValueConditionResolved(child2, isFactA, a, b).value();
        assertSame(a, cv3);

        Expression cv3b = EvaluateInlineConditional.conditionalValueConditionResolved(child2, isFactB, a, b).value();
        assertSame(a, cv3b);

        EvaluationContext child3 = minimalEvaluationContext.child(
                Or.or(minimalEvaluationContext, c, And.and(minimalEvaluationContext, a, b)));
        assertEquals("(a||c)&&(b||c)", child3.getConditionManager().absoluteState(child3).toString());
        Expression cv4 = EvaluateInlineConditional.conditionalValueConditionResolved(child3, isFactA, a, b).value();
        assertSame(b, cv4);
    }

    @Test
    public void test4() {
        Expression cv1 = inline(a, b, c);
        assertEquals("a?b:c", cv1.toString());
        Expression and1 = And.and(minimalEvaluationContext, a, cv1);
        assertEquals("a&&b", and1.toString());
        Expression and2 = And.and(minimalEvaluationContext, negate(a), cv1);
        assertEquals("!a&&c", and2.toString());
    }

    // ensure that the short-cuts in TestEqualsConstantInline do NOT apply
    // (a?b:c)==b does not guarantee a, rather a||b==c
    @Test
    public void test5() {
        Expression cv1 = inline(a, b, c);
        Expression eq = Equals.equals(minimalEvaluationContext, b, cv1);
        assertNotSame(a, eq);
        assertEquals("(a?b:c)==b", eq.toString());
    }

    @Test
    public void test6() {
        Expression cv1 = inline(a, p, NullConstant.NULL_CONSTANT);
        assertEquals("a?p:null", cv1.toString());
        Expression eq = Equals.equals(minimalEvaluationContext, NullConstant.NULL_CONSTANT, cv1);
        assertEquals("null==(a?p:null)", eq.toString());
        Expression eq2 = negate(Equals.equals(minimalEvaluationContext, NullConstant.NULL_CONSTANT, cv1));
        assertEquals("a&&null!=p", eq2.toString());
    }

    @Test
    public void test7() {
        Expression cv1 = inline(a, inline(b, newInt(3), newInt(4)), inline(c, newInt(2), newInt(5)));
        Expression eq2 = Equals.equals(minimalEvaluationContext, newInt(2), cv1);
        assertEquals("!a&&c", eq2.toString());
        Expression eq3 = Equals.equals(minimalEvaluationContext, newInt(3), cv1);
        assertEquals("a&&b", eq3.toString());
        Expression eq4 = Equals.equals(minimalEvaluationContext, newInt(4), cv1);
        assertEquals("a&&!b", eq4.toString());
        Expression eq5 = Equals.equals(minimalEvaluationContext, newInt(5), cv1);
        assertEquals("!a&&!c", eq5.toString());
    }

    @Test
    public void testIfStatements2() {
        Expression e1 = inline(a, newInt(3), inline(a, newInt(4), newInt(5)));
        assertEquals("a?3:5", e1.toString());
    }

    @Test
    public void testLoops4_0() {
        Expression e1 = inline(a, newInt(3), inline(negate(a), newInt(4), newInt(5)));
        assertEquals("a?3:4", e1.toString());
    }

    @Test
    public void testLoops4_1() {
        Expression ge10 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(10), true);
        assertEquals("i>=10", ge10.toString());
        Expression le9 = GreaterThanZero.less(minimalEvaluationContext, i, newInt(9), true);
        assertEquals("i<=9", le9.toString());
        Expression notLe9 = negate(le9);
        assertEquals(ge10, notLe9);
        Expression notGe10 = negate(ge10);
        assertEquals(le9, notGe10);
    }

    @Test
    public void testListUtil() {
        Expression e1 = inline(a, newInt(3), inline(b, newInt(4), newInt(5)));
        assertEquals("a?3:b?4:5", e1.toString());
        Expression notAAndNotB = newAndAppend(negate(a), negate(b));
        InlineConditional e2 = (InlineConditional) inline(notAAndNotB, newInt(2), e1);
        assertEquals("!a&&!b?2:a?3:b?4:5", e2.toString());
        EvaluationResult er = e2.evaluate(minimalEvaluationContext, ForwardEvaluationInfo.DEFAULT);
        assertEquals("!a&&!b?2:a?3:4", er.getExpression().toString());
        assertEquals("!a&&!b?2:a?3:4", e2.optimise(minimalEvaluationContext).toString());
    }

    @Test
    public void testDoubleInline() {
        Expression e1 = inline(a, inline(b, newInt(3), newInt(4)), newInt(4));
        assertEquals("a&&b?3:4", e1.toString());
        Expression e1b = inline(a, inline(b, newInt(4), newInt(3)), newInt(4));
        assertEquals("a&&!b?3:4", e1b.toString());
        Expression e2 = inline(a, inline(b, newInt(3), newInt(4)), newInt(5));
        assertEquals("a?b?3:4:5", e2.toString());

        Expression e3 = inline(a, newInt(4), inline(b, newInt(3), newInt(4)));
        assertEquals("a||!b?4:3", e3.toString());
        Expression e4 = inline(a, newInt(4), inline(b, newInt(4), newInt(3)));
        assertEquals("a||b?4:3", e4.toString());
    }

    @Test
    public void inlineInInline() {
        Expression e1 = inline(newAndAppend(a, b), inline(newAndAppend(b, c), newInt(3), newInt(4)), newInt(5));
        assertEquals("a&&b?c?3:4:5", e1.toString());
    }

    @Test
    public void inlineOr() {
        Expression e1 = inline(a, newOrAppend(a, b), c);
        assertEquals("a||c", e1.toString());

        Expression e2 = inline(a, b, newOrAppend(negate(a), c));
        assertEquals("!a||b", e2.toString());

        Expression e3 = inline(a, newOrAppend(negate(a), b), c);
        assertEquals("a?b:c", e3.toString());

        Expression e4 = inline(a, b, newOrAppend(a, c));
        assertEquals("a?b:c", e4.toString());
    }

    @Test
    public void inlineAnd() {
        Expression e1 = inline(a, newAndAppend(a, b), c);
        assertEquals("a?b:c", e1.toString());

        Expression e2 = inline(a, newAndAppend(negate(a), b), c);
        assertEquals("!a&&c", e2.toString());

        Expression e3 = inline(a, b, newAndAppend(a, c));
        assertEquals("a&&b", e3.toString());

        Expression e4 = inline(a, b, newAndAppend(negate(a), c));
        assertEquals("a?b:c", e4.toString());
    }

    @Test
    public void testReturnType() {
        CausesOfDelay delay = new CausesOfDelay.SimpleSet(Location.NOT_YET_SET, CauseOfDelay.Cause.INITIAL_VALUE);
        Expression a = DelayedExpression.forState(PRIMITIVES.booleanParameterizedType(), LinkedVariables.delayedEmpty(delay), delay);
        ParameterizedType boxed = PRIMITIVES.boxedBooleanTypeInfo().asParameterizedType(InspectionProvider.DEFAULT);
        Expression b = new UnknownExpression(boxed, "return value");
        Expression inline = inline(c, a, b);
        assertEquals("c?<s:boolean>:<return value>", inline.toString());
        assertEquals(boxed, inline.returnType());
        Expression inline2 = inline(c, b, a);
        assertEquals("c?<return value>:<s:boolean>", inline2.toString());
        assertEquals(boxed, inline2.returnType());
    }

    @Test
    public void testAeqBThenAElseB() {
        Expression inline = inline(equals(i, j), i, j);
        assertEquals(j, inline);
        Expression inline2 = inline(equals(i, j), j, i);
        assertEquals(i, inline2);
    }
}
