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

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.e2immu.analyser.model.expression.NullConstant.NULL_CONSTANT;
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
        return EvaluateInlineConditional.conditionalValueConditionResolved(context,
                c, t, f, true, null).value();
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
    public void test4() {
        Expression cv1 = inline(a, b, c);
        assertEquals("a?b:c", cv1.toString());
        Expression and1 = And.and(context, a, cv1);
        assertEquals("a&&b", and1.toString());
        Expression and2 = And.and(context, negate(a), cv1);
        assertEquals("!a&&c", and2.toString());
    }

    // ensure that the short-cuts in TestEqualsConstantInline do NOT apply
    // (a?b:c)==b does not guarantee a, rather a||b==c
    @Test
    public void test5() {
        Expression cv1 = inline(a, b, c);
        Expression eq = Equals.equals(context, b, cv1);
        assertNotSame(a, eq);
        assertEquals("(a?b:c)==b", eq.toString());
    }

    @Test
    public void test6() {
        Expression cv1 = inline(a, p, NULL_CONSTANT);
        assertEquals("a?p:null", cv1.toString());
        Expression eq = Equals.equals(context, NULL_CONSTANT, cv1);
        assertEquals("!a||null==p", eq.toString());
        Expression eq2 = negate(Equals.equals(context, NULL_CONSTANT, cv1));
        assertEquals("a&&null!=p", eq2.toString());
        assertTrue(newAndAppend(eq, eq2).isBoolValueFalse());
    }

    @Test
    public void test7() {
        Expression cv1 = inline(a, inline(b, newInt(3), newInt(4)), inline(c, newInt(2), newInt(5)));
        Expression eq2 = Equals.equals(context, newInt(2), cv1);
        assertEquals("!a&&c", eq2.toString());
        Expression eq3 = Equals.equals(context, newInt(3), cv1);
        assertEquals("a&&b", eq3.toString());
        Expression eq4 = Equals.equals(context, newInt(4), cv1);
        assertEquals("a&&!b", eq4.toString());
        Expression eq5 = Equals.equals(context, newInt(5), cv1);
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
        Expression ge10 = GreaterThanZero.greater(context, i, newInt(10), true);
        assertEquals("i>=10", ge10.toString());
        Expression le9 = GreaterThanZero.less(context, i, newInt(9), true);
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
        EvaluationResult er = e2.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("!a&&!b?2:a?3:4", er.getExpression().toString());
        assertEquals("!a&&!b?2:a?3:4", e2.optimise(context, null).toString());
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
        CausesOfDelay delay = DelayFactory.createDelay(LocationImpl.NOT_YET_SET, CauseOfDelay.Cause.INITIAL_VALUE);
        Expression a = DelayedExpression.forState(Identifier.generate("test"), PRIMITIVES.booleanParameterizedType(),
              EmptyExpression.EMPTY_EXPRESSION,  delay);
        ParameterizedType boxed = PRIMITIVES.boxedBooleanTypeInfo().asParameterizedType(InspectionProvider.DEFAULT);
        Expression b = UnknownExpression.forReturnVariable(Identifier.constant("unknown"), boxed);
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

    // just to check that s==null ? null: s === s
    @Test
    public void aIsNull() {
        Expression inline = inline(equals(s, NULL_CONSTANT), NULL_CONSTANT, s);
        assertEquals("s", inline.toString());
    }

    @Test
    public void equalsNull() {
        Expression e = equals(NULL_CONSTANT, inline(a, s1, NULL_CONSTANT));
        assertEquals("!a||null==s1", e.toString());
        Expression e2 = equals(NULL_CONSTANT, inline(a, NULL_CONSTANT, s1));
        assertEquals("a||null==s1", e2.toString());
    }

    @Test
    public void testComplex() {
        Expression nmGetIsNull = equals(NULL_CONSTANT, s1);
        Expression nmIsNull = equals(NULL_CONSTANT, s2);
        Expression condition = newAndAppend(a, newOrAppend(nmGetIsNull, nmIsNull));
        Expression ifFalse = inline(a, s1, s3);
        Expression base = inline(condition, NULL_CONSTANT, ifFalse);
        assertEquals("a&&(null==s1||null==s2)?null:a?s1:s3", base.toString());

        Expression baseNotNull = negate(equals(NULL_CONSTANT, base));
        assertEquals("null!=(a?s1:s3)&&(!a||null!=s1)&&(!a||null!=s2)", baseNotNull.toString());

        Expression baseNull = equals(NULL_CONSTANT, base);
        assertEquals("(a||null==(a?s1:s3))&&(null==s1||null==s2||null==(a?s1:s3))", baseNull.toString());

        Expression and = newAndAppend(baseNotNull, baseNull);
        assertTrue(and.isBoolValueFalse());
        Expression and2 = newAndAppend(baseNull, baseNotNull);
        assertTrue(and2.isBoolValueFalse());
    }
}
