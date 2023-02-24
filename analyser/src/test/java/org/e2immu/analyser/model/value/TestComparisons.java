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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.InequalitySolver;
import org.e2immu.analyser.model.variable.FieldReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestComparisons extends CommonAbstractValue {

    @Test
    public void testNegate1() {
        GreaterThanZero gt3 = (GreaterThanZero) GreaterThanZero.greater(context, i, newInt(3), false);
        assertEquals("i>=4", gt3.toString());
        GreaterThanZero notGt3 = (GreaterThanZero) gt3.negate(context);
        assertEquals("i<4", notGt3.toString());
    }

    @Test
    public void testNegate2() {
        GreaterThanZero gt3 = (GreaterThanZero) GreaterThanZero.greater(context, i, newInt(3), true);
        assertEquals("i>=3", gt3.toString());
        GreaterThanZero notGt3 = (GreaterThanZero) gt3.negate(context);
        assertEquals("i<3", notGt3.toString());
    }

    @Test
    public void testNegate3() {
        GreaterThanZero gt3 = (GreaterThanZero) GreaterThanZero.greater(context, newInt(3), i, false);
        assertEquals("i<3", gt3.toString());
        GreaterThanZero notGt3 = (GreaterThanZero) gt3.negate(context);
        assertEquals("i>=3", notGt3.toString());
    }

    @Test
    public void testNegate4() {
        GreaterThanZero gt3 = (GreaterThanZero) GreaterThanZero.greater(context, newInt(3), i, true);
        assertEquals("i<4", gt3.toString());
        GreaterThanZero notGt3 = (GreaterThanZero) gt3.negate(context);
        assertEquals("i>=4", notGt3.toString());
    }

    @Test
    public void testXb() {
        GreaterThanZero gt3 = (GreaterThanZero) GreaterThanZero.greater(context, i, newInt(3), false);
        assertEquals("i>=4", gt3.toString());
        GreaterThanZero.XB xb = gt3.extract(context);
        assertNotNull(xb);
        assertTrue(gt3.allowEquals());
        assertEquals(4, (int) xb.b());
        assertEquals(i, xb.x());
        assertFalse(xb.lessThan());
    }

    @Test
    public void testXb2() {
        GreaterThanZero lt3 = (GreaterThanZero) GreaterThanZero.less(context, i, newInt(3), false);
        assertEquals("i<3", lt3.toString());
        GreaterThanZero.XB xb = lt3.extract(context);
        assertNotNull(xb);
        assertFalse(lt3.allowEquals());
        assertEquals(2, (int) xb.b());
        assertEquals(i, xb.x());
        assertTrue(xb.lessThan());
    }

    @Test
    public void testEqualsEquals() {
        Expression iEq4 = equals(i, newInt(4));
        Expression iEq3 = equals(newInt(3), i);
        Expression and = newAndAppend(iEq3, iEq4);
        assertEquals(FALSE, and);
    }

    @Test
    public void testEqualsNotEquals() {
        Expression iEq4 = equals(i, newInt(4));
        assertEquals("4==i", iEq4.toString());
        Expression iNeq3 = negate(equals(newInt(3), i));
        assertEquals("3!=i", iNeq3.toString());
        Expression and = newAndAppend(iNeq3, iEq4);
        assertEquals(iEq4, and);
    }

    @Test
    public void testEqualsGreaterThan0() {
        Expression iEq4 = equals(i, newInt(4));
        Expression iGe0 = GreaterThanZero.greater(context, i, newInt(0), true);
        Expression and = newAndAppend(iGe0, iEq4);
        assertEquals(iEq4, and);

        Expression iEqMinus4 = equals(i, newInt(-4));
        Expression and2 = newAndAppend(iGe0, iEqMinus4);
        assertEquals(FALSE, and2);

        Expression iEq0 = equals(i, newInt(0));
        Expression and4 = newAndAppend(iGe0, iEq0);
        assertEquals(iEq0, and4);
    }

    // GE1 follows a different path from GE0, since -1 + x >= 0 is involved
    @Test
    public void testEqualsGreaterThan1() {
        Expression iEq4 = equals(i, newInt(4));
        Expression iGe1 = GreaterThanZero.greater(context, i, newInt(1), true);
        Expression and = newAndAppend(iGe1, iEq4);
        assertEquals(iEq4, and);

        Expression iEqMinus4 = equals(i, newInt(-4));
        Expression and2 = newAndAppend(iGe1, iEqMinus4);
        assertEquals(FALSE, and2);

        Expression iEq0 = equals(i, newInt(0));
        Expression iGt0 = GreaterThanZero.greater(context, i, newInt(0), false);
        Expression and3 = newAndAppend(iGt0, iEq0);
        assertEquals(FALSE, and3);
    }

    // GE1 follows a different path from GE0, since -1 + x >= 0 is involved
    @Test
    public void testEqualsLessThan1() {
        Expression iEq4 = equals(i, newInt(4));
        Expression iLe1 = GreaterThanZero.less(context, i, newInt(1), true);
        Expression and = newAndAppend(iLe1, iEq4);
        assertEquals(FALSE, and);

        Expression iEqMinus4 = equals(i, newInt(-4));
        Expression and2 = newAndAppend(iLe1, iEqMinus4);
        assertEquals(iEqMinus4, and2);

        Expression iEq0 = equals(i, newInt(0));
        Expression iLt0 = GreaterThanZero.less(context, i, newInt(0), false);
        Expression and3 = newAndAppend(iLt0, iEq0);
        assertEquals(FALSE, and3);
    }

    @Test
    public void test1() {
        Expression iGe0 = GreaterThanZero.greater(context, i, newInt(0), true);
        assertEquals("i>=0", iGe0.toString());
        Expression iGe3 = GreaterThanZero.greater(context, i, newInt(3), true);
        assertEquals("i>=3", iGe3.toString());
        Expression and = newAndAppend(iGe0, iGe3);
        assertEquals(iGe3, and);
        Expression and2 = newAndAppend(iGe3, iGe0);
        assertEquals(iGe3, and2);
    }

    // i <= 0 && i <= 3 ==> i <= 0
    @Test
    public void test2() {
        Expression iLe0 = GreaterThanZero.less(context, i, newInt(0), true);
        assertEquals("i<1", iLe0.toString());
        Expression iLe3 = GreaterThanZero.less(context, i, newInt(3), true);
        assertEquals("i<4", iLe3.toString()); // even though ugly, formatting is correct
        Expression and = newAndAppend(iLe0, iLe3);
        assertEquals(iLe0, and);
    }

    @Test
    public void test3() {
        Expression iLe0 = GreaterThanZero.less(context, i, newInt(0), true);
        assertEquals("i<1", iLe0.toString());
        Expression iGe3 = GreaterThanZero.greater(context, i, newInt(3), true);
        assertEquals("i>=3", iGe3.toString());
        Expression and = newAndAppend(iLe0, iGe3);
        assertEquals(FALSE, and);
        Expression and2 = newAndAppend(iGe3, iLe0);
        assertEquals(FALSE, and2);
    }

    @Test
    public void test4() {
        Expression iGe0 = GreaterThanZero.greater(context, i, newInt(0), true);
        assertEquals("i>=0", iGe0.toString());
        Expression iLe3 = GreaterThanZero.less(context, i, newInt(3), true);
        assertEquals("i<4", iLe3.toString());
        Expression and = newAndAppend(iGe0, iLe3);
        assertTrue(and instanceof And);
        Expression and2 = newAndAppend(iLe3, iGe0);
        assertTrue(and2 instanceof And);
    }

    @Test
    public void test5() {
        Expression iGe0 = GreaterThanZero.greater(context, i, newInt(0), true);
        assertEquals("i>=0", iGe0.toString());
        Expression iGt0 = GreaterThanZero.greater(context, i, newInt(0), false);
        assertEquals("i>=1", iGt0.toString());
        Expression and = newAndAppend(iGe0, iGt0);
        assertEquals(iGt0, and);
    }

    @Test
    public void test6() {
        Expression iGe0 = GreaterThanZero.greater(context, i, newInt(0), true);
        assertEquals("i>=0", iGe0.toString());
        GreaterThanZero iLe0 = (GreaterThanZero) GreaterThanZero.less(context, i, newInt(0), true);
        assertEquals("i<1", iLe0.toString());
        GreaterThanZero.XB xbLe0 = iLe0.extract(context);
        assertEquals(0.0d, xbLe0.b());
        assertTrue(xbLe0.lessThan());
        Expression and = newAndAppend(iGe0, iLe0);
        assertEquals("0==i", and.toString());
    }

    @Test
    public void test7() {
        Expression iGe0 = GreaterThanZero.greater(context, i, newInt(0), false);
        assertEquals("i>=1", iGe0.toString());
        Expression iLe0 = GreaterThanZero.less(context, i, newInt(0), false);
        assertEquals("i<0", iLe0.toString());
        Expression and = newAndAppend(iGe0, iLe0);
        assertEquals(FALSE, and);
        Expression and2 = newAndAppend(iLe0, iGe0);
        assertEquals(FALSE, and2);
    }

    @Test
    public void testGEZeroLZero() {
        GreaterThanZero iLt0 = (GreaterThanZero) GreaterThanZero.less(context, i, newInt(0), false);
        assertEquals("i<0", iLt0.toString());
        GreaterThanZero.XB xbLt0 = iLt0.extract(context);
        assertEquals(-1.0, xbLt0.b());
        Expression iGe0 = GreaterThanZero.greater(context, i, newInt(0), true);
        assertEquals("i>=0", iGe0.toString());

        assertEquals("false", newAndAppend(iLt0, iGe0).toString());
        assertEquals("false", newAndAppend(iGe0, iLt0).toString());
    }

    // (p>=3||p<=2)&&(p>=3||q>=5)&&(p<=2||q<=-1)&&(p<=2||q<0) &&( q>=5||q<=-1) && (q>=5||q<0)
    // problem seems to be that q<0 should never arise -> see second test!
    @Test
    public void testCC7() {
        Expression iLt0 = GreaterThanZero.less(context, i, newInt(0), false);
        Expression iLeM1 = GreaterThanZero.less(context, i, newInt(-1), true);
        assertEquals(iLt0, iLeM1);

        Expression jGt5 = GreaterThanZero.greater(context, j, newInt(5), true);

        Expression or1 = newOrAppend(iLt0, jGt5);
        assertEquals("i<0||j>=5", or1.toString());
        Expression or2 = newOrAppend(iLeM1, jGt5);
        assertEquals("i<0||j>=5", or2.toString());

        assertEquals(or1, or2);

        Expression negOr1 = negate(or1);
        assertEquals("i>=0&&j<5", negOr1.toString());
    }

    @Test
    public void testCC7_2() {
        Expression iLt0 = GreaterThanZero.less(context, i, newInt(2), true);
        Expression jGe0 = GreaterThanZero.greater(context, j, newInt(0), true);
        Expression or1 = newOrAppend(iLt0, jGe0);
        assertEquals("i<3||j>=0", or1.toString());
        Expression notOr1 = negate(or1);
        assertEquals("i>=3&&j<0", notOr1.toString());
    }

    @Test
    public void testEvE2Imm7() {
        Expression iGt0 = GreaterThanZero.greater(context, i, newInt(0), false);
        Expression jLe0 = GreaterThanZero.less(context, j, newInt(0), true);
        Expression iGt0jLe0 = newAndAppend(iGt0, jLe0);
        assertEquals("i>=1&&j<1", iGt0jLe0.toString());

        // j>=i === j-i>=0
        Expression jMinusI = Sum.sum(context, j, negate(i));
        Expression jGeI = GreaterThanZero.greater(context, jMinusI, newInt(0), true);
        assertEquals("j>=i", jGeI.toString());

        // clearly, the j>=i is false:
        InequalitySolver inequalitySolver = new InequalitySolver(context, iGt0jLe0);
        assertFalse(inequalitySolver.evaluate(jGeI));

        // this should be incorporated into And
        Expression combination = newAndAppend(iGt0jLe0, jGeI);
        assertTrue(combination.isBoolValueFalse(), "Have " + combination);
    }

    @Test
    public void testEvE2Imm7b() {
        Expression iGe1 = GreaterThanZero.greater(context, i, newInt(1), true);
        Expression jEq0 = equals(j, newInt(0));
        Expression jGeI = GreaterThanZero.greater(context, j, i, true);
        Expression and = newAndAppend(iGe1, jEq0, jGeI);
        assertTrue(and.isBoolValueFalse(), "Have " + and);
    }

    @Test
    public void testEvE2Imm7c() {
        Expression iGe0 = GreaterThanZero.greater(context, i, newInt(0), true);
        Expression jGe0 = GreaterThanZero.greater(context, j, newInt(0), true);
        Expression jGeI = GreaterThanZero.greater(context, j, i, true);
        Expression and1 = newAndAppend(iGe0, jGe0, jGeI);
        assertEquals("i>=0&&j>=0&&j>=i", and1.toString());
        Expression iGe1 = GreaterThanZero.greater(context, i, newInt(1), true);
        Expression jLe0 = GreaterThanZero.less(context, j, newInt(0), true);
        Expression and2 = newAndAppend(iGe1, jLe0);
        assertEquals("i>=1&&j<1", and2.toString());
        Expression and = newAndAppend(and1, and2);
        assertTrue(and.isBoolValueFalse(), "Have " + and);
    }

    @Test
    public void testEvE2Imm8a() {
        Expression iNot0 = negate(equals(i, newInt(0)));
        Expression iGe0 = GreaterThanZero.greater(context, i, newInt(0), true);
        Expression combined = newAndAppend(iNot0, iGe0);
        assertEquals("i>=1", combined.toString());
    }

    @Test
    public void testEvE2Imm8() {
        Expression iNot0 = negate(equals(i, newInt(0)));
        Expression jEq0 = equals(j, newInt(0));
        Expression iGe0 = GreaterThanZero.greater(context, i, newInt(0), true);
        Expression combined = newAndAppend(iNot0, jEq0, iGe0);
        // currently: 0!=i && 0==j && i>=0
        assertEquals("i>=1&&0==j", combined.toString());

        Expression jGeI = GreaterThanZero.greater(context, j, i, true);
        assertEquals("j>=i", jGeI.toString());
        assertEquals(2, jGeI.variables(true).size());
        Expression combo2 = newAndAppend(combined, jGeI);
        assertTrue(combo2.isBoolValueFalse(), "Got: " + combo2);
    }

    @Test
    public void testLoops7() {
        Instance i1 = Instance.forLoopVariable(Identifier.CONSTANT, vi, EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
        Expression iMinusOne = Sum.sum(context, i, newInt(-1));
        Expression iGtI1 = GreaterThanZero.greater(context,iMinusOne, i1, true);
        assertEquals("-1-(instance type int)+i>=0", iGtI1.toString());
        Expression iLeI2 = GreaterThanZero.less(context, i, i1, true);
        assertEquals("instance type int>=i", iLeI2.toString());
        assertEquals("false", newAndAppend(iGtI1, iLeI2).toString());
        assertEquals("false", newAndAppend(iLeI2, iGtI1).toString());

        Expression iGeI1 = GreaterThanZero.greater(context, i, i1, true);
        assertEquals("instance type int==i", newAndAppend(iGeI1, iLeI2).toString());
    }

    @Test
    public void testDelayed() {
        Expression delayedPGe0 = GreaterThanZero.greater(context, delayedP, newInt(0), true);
        assertEquals("<p:p>>=0", delayedPGe0.toString());
        assertEquals("[some.type.type(String):0:p]", delayedPGe0.variables(true).toString());
        assertTrue(delayedPGe0.variables(true).stream()
                .allMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference));
    }

    @Test
    public void testLoops1() {
        Instance i1 = Instance.forLoopVariable(Identifier.CONSTANT, vi, EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);

        // -1-(instance type int)+i>=1
        Expression s1 = Sum.sum(context, newInt(-1), Negation.negate(context, i1));
        Expression s2 = Sum.sum(context, s1, i);
        Expression e1 = GreaterThanZero.greater(context, s2, newInt(1), true);
        if (e1 instanceof GreaterThanZero gt) {
            assertEquals("-2-(instance type int)+i", gt.expression().toString());
        } else fail();

        assertEquals("-2-(instance type int)+i>=0", e1.toString());
        GreaterThanZero.XB extract1 = ((GreaterThanZero) e1).extract(context);
        assertEquals("-(instance type int)+i", extract1.x().toString());
        assertEquals(2.0d, extract1.b());
        assertFalse(extract1.lessThan());

        // 1+instance type int>=i
        Expression s3 = Sum.sum(context, newInt(1), i1);
        Expression e2 = GreaterThanZero.greater(context, s3, i, true);
        assertEquals("1+instance type int>=i", e2.toString());

        GreaterThanZero.XB extract2 = ((GreaterThanZero) e2).extract(context);
        assertEquals("instance type int-i", extract2.x().toString());
        assertEquals(-1.0d, extract2.b());
        assertFalse(extract2.lessThan());

        // solution: must be constant false (simplify to n>=i+2 && n <=i+1)
        assertEquals("false", newAndAppend(e1, e2).toString());
        assertEquals("false", newAndAppend(e2, e1).toString());
    }

    @Test
    public void testRange() {
        Expression gt0 = GreaterThanZero.greater(context, i, newInt(0), true);
        Expression lt10 = GreaterThanZero.less(context, i, newInt(10), false);
        Expression eq10 = Equals.equals(context, i, newInt(10));
        assertEquals("false", newAndAppend(gt0, lt10, eq10).toString());
        assertEquals("false", newAndAppend(eq10, gt0, lt10).toString());
    }

    @Test
    public void testNumeric() {
        Expression iGej = GreaterThanZero.greater(context, i, j, true);
        assertEquals("i>=j", iGej.toString());
        Expression iLtj = GreaterThanZero.greater(context, Sum.sum(context, j, newInt(-1)), i, true);
        assertEquals("-1-i+j>=0", iLtj.toString());
        assertEquals("false", newAndAppend(iGej, iLtj).toString());
        assertEquals("false", newAndAppend(iLtj, iGej).toString());
    }

    @Test
    public void testNumericNegation() {
        Expression iGej = GreaterThanZero.greater(context, i, j, true);
        Expression jMinusOne = Sum.sum(context, j, newInt(-1));
        Expression jGti = GreaterThanZero.greater(context, jMinusOne, i, true);
        assertEquals("-1-i+j>=0", jGti.toString());
        Expression notJGti = negate(jGti);
        assertEquals("i>=j", notJGti.toString());
        assertEquals(iGej, notJGti);
    }

}
