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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.Primitives;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestEqualsConstantInline extends CommonAbstractValue {

    private static InlineConditional inline(Expression c, Expression a, Expression b) {
        return new InlineConditional(Identifier.generate(), minimalEvaluationContext.getAnalyserContext(), c, a, b);
    }

    //(a ? null: b) == null with guaranteed b != null --> a
    // in this particular case, a === an==null
    @Test
    public void test1() {
        InlineConditional inlineConditional = inline(
                Equals.equals(minimalEvaluationContext, NullConstant.NULL_CONSTANT, an),
                NullConstant.NULL_CONSTANT,
                b);
        Expression result = Equals.tryToRewriteConstantEqualsInline(minimalEvaluationContext,
                NullConstant.NULL_CONSTANT, inlineConditional);
        assertNotNull(result);
        assertEquals("null==an", result.toString());
    }

    //(a ? b: null) == null with guaranteed b != null --> !a
    // in this particular case, a === an==null
    @Test
    public void test2() {
        InlineConditional inlineConditional = inline(
                Equals.equals(minimalEvaluationContext, NullConstant.NULL_CONSTANT, an),
                b, NullConstant.NULL_CONSTANT);
        Expression result = Equals.tryToRewriteConstantEqualsInline(minimalEvaluationContext,
                NullConstant.NULL_CONSTANT, inlineConditional);
        assertNotNull(result);
        assertEquals("null!=an", result.toString());
    }

    //(a ? b: c) == null with guaranteed b != null, c not guaranteed --> !a && c == null

    // here, s is nullable == not guaranteed to be not-null
    @Test
    public void test3() {
        InlineConditional inlineConditional = inline(
                a, new StringConstant(analyserContext.getPrimitives(), "x"), s);
        Expression result = Equals.tryToRewriteConstantEqualsInline(minimalEvaluationContext,
                NullConstant.NULL_CONSTANT, inlineConditional);
        assertNotNull(result);
        assertEquals("!a&&null==s", result.toString());
    }

    // (a ? "x": b) == "y"  ==> !a&&"y"==b
    @Test
    public void test4() {
        InlineConditional inlineConditional = inline(
                a, new StringConstant(analyserContext.getPrimitives(), "x"), s);
        Expression result = Equals.tryToRewriteConstantEqualsInline(minimalEvaluationContext,
                new StringConstant(analyserContext.getPrimitives(), "y"), inlineConditional);
        assertNotNull(result);
        assertEquals("!a&&\"y\"==s", result.toString());
    }

    @Test
    public void test5() {
        InlineConditional inlineConditional1 = inline(
                a, new StringConstant(analyserContext.getPrimitives(), "x"), s);
        InlineConditional inlineConditional2 = inline(
                a, s, p);
        Expression result = Equals.tryToRewriteConstantEqualsInline(minimalEvaluationContext,
                inlineConditional1, inlineConditional2);
        assertNotNull(result);
        assertEquals("\"x\"==s&&s==p", result.toString());
    }

    @Test
    public void test6() {
        InlineConditional inlineConditional1 = inline(
                a, new StringConstant(analyserContext.getPrimitives(), "x"), s);
        InlineConditional inlineConditional2 = inline(
                a, s, new StringConstant(analyserContext.getPrimitives(), "y"));
        Expression result = Equals.tryToRewriteConstantEqualsInline(minimalEvaluationContext,
                inlineConditional1, inlineConditional2);
        assertNotNull(result);
        assertEquals("false", result.toString());
    }

    @Test
    public void test7() {
        InlineConditional inlineConditional1 = inline(
                a, new StringConstant(analyserContext.getPrimitives(), "x"), s);
        InlineConditional inlineConditional2 = inline(
                a, s, new StringConstant(analyserContext.getPrimitives(), "y"));
        Expression result = Equals.tryToRewriteConstantEqualsInlineNegative(minimalEvaluationContext,
                inlineConditional1, inlineConditional2);
        assertNotNull(result);
        assertEquals("\"x\"!=s||\"y\"!=s", result.toString());
    }

    @Test
    public void test8() {
        InlineConditional inlineConditional1 = inline(
                a, new StringConstant(analyserContext.getPrimitives(), "x"), s);
        InlineConditional inlineConditional2 = inline(
                a, s, p);
        Expression result = Equals.tryToRewriteConstantEqualsInlineNegative(minimalEvaluationContext,
                inlineConditional1, inlineConditional2);
        assertNotNull(result);
        assertEquals("\"x\"!=s||s!=p", result.toString());
    }

    @Test
    public void test9() {
        InlineConditional inlineConditional1 = inline(
                a, new StringConstant(analyserContext.getPrimitives(), "x"), s);
        Expression result = Equals.tryToRewriteConstantEqualsInlineNegative(minimalEvaluationContext,
                inlineConditional1, inlineConditional1);
        assertNotNull(result);
        assertEquals("false", result.toString());
    }


    // (a ? "x": b) != "x"  ==> !a&&"x"!=b
    @Test
    public void test10() {
        InlineConditional inlineConditional = inline(
                a, new StringConstant(analyserContext.getPrimitives(), "x"), s);
        Expression result = Equals.tryToRewriteConstantEqualsInlineNegative(minimalEvaluationContext,
                new StringConstant(analyserContext.getPrimitives(), "x"), inlineConditional);
        assertNotNull(result);
        assertEquals("!a&&\"x\"!=s", result.toString());
    }

    // (a ? b: "x") != "x"  ==> a&&"x"!=b
    @Test
    public void test11() {
        InlineConditional inlineConditional = inline(
                a, s, new StringConstant(analyserContext.getPrimitives(), "x"));
        Expression result = Equals.tryToRewriteConstantEqualsInlineNegative(minimalEvaluationContext,
                new StringConstant(analyserContext.getPrimitives(), "x"), inlineConditional);
        assertNotNull(result);
        assertEquals("a&&\"x\"!=s", result.toString());
    }

    @Test
    public void test12() {
        InlineConditional inlineConditional = inline(
                a, s, new StringConstant(analyserContext.getPrimitives(), "x"));
        Expression result = Negation.negate(minimalEvaluationContext, Equals.equals(minimalEvaluationContext,
                new StringConstant(analyserContext.getPrimitives(), "x"), inlineConditional));
        assertNotNull(result);
        assertEquals("a&&\"x\"!=s", result.toString());
    }

    @Test
    public void test13() {
        InlineConditional inlineConditional = inline(
                a, new StringConstant(analyserContext.getPrimitives(), "x"), s);
        Expression result = Negation.negate(minimalEvaluationContext, Equals.equals(minimalEvaluationContext,
                inlineConditional, new StringConstant(analyserContext.getPrimitives(), "x")));
        assertNotNull(result);
        assertEquals("!a&&\"x\"!=s", result.toString());
    }

    @Test
    public void test14() {
        boolean notNull = minimalEvaluationContext.isNotNull0(newInt(3), false);
        assertTrue(notNull);
    }

    @Test
    public void test15() {
        Expression cv1 = inline(b, newInt(3), NullConstant.NULL_CONSTANT);
        assertEquals("b?3:null", cv1.toString());
        Expression eqNull = Equals.equals(minimalEvaluationContext, NullConstant.NULL_CONSTANT, cv1);
        assertEquals("!b", eqNull.toString());
    }

    @Test
    public void test16() {

        // recursive example
        // a ? (b ? 3: null): (c ? null: 5) == null
        // now 3!=null, so a recursive application will result in b
        // now 5!=null, so a recursive application will result in !c
        // end: a ? b : !c  or (a&&b) || (!a&&!c)

        Expression cv1 = inline(a, inline(b, newInt(3), NullConstant.NULL_CONSTANT), inline(c, NullConstant.NULL_CONSTANT, newInt(5)));
        Expression eqNull = Equals.equals(minimalEvaluationContext, NullConstant.NULL_CONSTANT, cv1);
        assertEquals("(a||c)&&(!a||!b)&&(!b||c)", eqNull.toString());
    }

    @Test
    public void test17() {
        Expression cv1 = inline(a, inline(b, newInt(3), NullConstant.NULL_CONSTANT), i);
        assertEquals("a?b?3:null:i", cv1.toString());
        TypeInfo type = cv1.returnType().typeInfo;
        assertFalse(Primitives.isPrimitiveExcludingVoid(type));
        Expression eqNull = Equals.equals(minimalEvaluationContext, NullConstant.NULL_CONSTANT, cv1);
        assertEquals("a&&!b", eqNull.toString());
    }
}
