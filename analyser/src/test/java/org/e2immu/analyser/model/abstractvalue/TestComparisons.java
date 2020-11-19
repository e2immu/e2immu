package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.IntValue;
import org.junit.Assert;
import org.junit.Test;

public class TestComparisons extends CommonAbstractValue {

    @Test
    public void testNegate1() {
        GreaterThanZeroValue gt3 = (GreaterThanZeroValue) GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(3), false);
        Assert.assertEquals("((-4) + i) >= 0", gt3.toString()); // i >= 4
        GreaterThanZeroValue notGt3 = (GreaterThanZeroValue) gt3.negate(minimalEvaluationContext);
        Assert.assertEquals("(3 + (-(i))) >= 0", notGt3.toString()); // i <= 3
    }

    @Test
    public void testXb() {
        GreaterThanZeroValue gt3 = (GreaterThanZeroValue) GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(3), false);
        Assert.assertEquals("((-4) + i) >= 0", gt3.toString());
        GreaterThanZeroValue.XB xb = gt3.extract(minimalEvaluationContext);
        Assert.assertNotNull(xb);
        Assert.assertTrue(gt3.allowEquals);
        Assert.assertEquals(4, (int) xb.b);
        Assert.assertEquals(i, xb.x);
        Assert.assertFalse(xb.lessThan);
    }

    @Test
    public void testXb2() {
        GreaterThanZeroValue lt3 = (GreaterThanZeroValue) GreaterThanZeroValue.less(minimalEvaluationContext, i, newInt(3), false);
        Assert.assertEquals("(2 + (-(i))) >= 0", lt3.toString());
        GreaterThanZeroValue.XB xb = lt3.extract(minimalEvaluationContext);
        Assert.assertNotNull(xb);
        Assert.assertTrue(lt3.allowEquals);
        Assert.assertEquals(2, (int) xb.b);
        Assert.assertEquals(i, xb.x);
        Assert.assertTrue(xb.lessThan);
    }

    @Test
    public void testEqualsEquals() {
        Value iEq4 = equals(i, newInt(4));
        Value iEq3 = equals(newInt(3), i);
        Value and = newAndAppend(iEq3, iEq4);
        Assert.assertEquals(FALSE, and);
    }

    @Test
    public void testEqualsNotEquals() {
        Value iEq4 = equals(i, newInt(4));
        Assert.assertEquals("4 == i", iEq4.toString());
        Value iNeq3 = negate(equals(newInt(3), i));
        Assert.assertEquals("not (3 == i)", iNeq3.toString());
        Value and = newAndAppend(iNeq3, iEq4);
        Assert.assertEquals(iEq4, and);
    }

    @Test
    public void testEqualsGreaterThan0() {
        Value iEq4 = equals(i, newInt(4));
        Value iGe0 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(0), true);
        Value and = newAndAppend(iGe0, iEq4);
        Assert.assertEquals(iEq4, and);

        Value iEqMinus4 = equals(i, newInt(-4));
        Value and2 = newAndAppend(iGe0, iEqMinus4);
        Assert.assertEquals(FALSE, and2);

        Value iEq0 = equals(i, newInt(0));
        Value and4 = newAndAppend(iGe0, iEq0);
        Assert.assertEquals(iEq0, and4);
    }

    // GE1 follows a different path from GE0, since -1 + x >= 0 is involved
    @Test
    public void testEqualsGreaterThan1() {
        Value iEq4 = equals(i, newInt(4));
        Value iGe1 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(1), true);
        Value and = newAndAppend(iGe1, iEq4);
        Assert.assertEquals(iEq4, and);

        Value iEqMinus4 = equals(i, newInt(-4));
        Value and2 = newAndAppend(iGe1, iEqMinus4);
        Assert.assertEquals(FALSE, and2);

        Value iEq0 = equals(i, newInt(0));
        Value iGt0 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(0), false);
        Value and3 = newAndAppend(iGt0, iEq0);
        Assert.assertEquals(FALSE, and3);
    }

    // GE1 follows a different path from GE0, since -1 + x >= 0 is involved
    @Test
    public void testEqualsLessThan1() {
        Value iEq4 = equals(i, newInt(4));
        Value iLe1 = GreaterThanZeroValue.less(minimalEvaluationContext, i, newInt(1), true);
        Value and = newAndAppend(iLe1, iEq4);
        Assert.assertEquals(FALSE, and);

        Value iEqMinus4 = equals(i, newInt(-4));
        Value and2 = newAndAppend(iLe1, iEqMinus4);
        Assert.assertEquals(iEqMinus4, and2);

        Value iEq0 = equals(i, newInt(0));
        Value iLt0 = GreaterThanZeroValue.less(minimalEvaluationContext, i, newInt(0), false);
        Value and3 = newAndAppend(iLt0, iEq0);
        Assert.assertEquals(FALSE, and3);
    }

    @Test
    public void test1() {
        Value iGe0 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(0), true);
        Assert.assertEquals("i >= 0", iGe0.toString());
        Value iGe3 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(3), true);
        Assert.assertEquals("((-3) + i) >= 0", iGe3.toString());
        Value and = newAndAppend(iGe0, iGe3);
        Assert.assertEquals(iGe3, and);
        Value and2 = newAndAppend(iGe3, iGe0);
        Assert.assertEquals(iGe3, and2);
    }

    // i <= 0 && i <= 3 ==> i <= 0
    @Test
    public void test2() {
        Value iLe0 = GreaterThanZeroValue.less(minimalEvaluationContext, i, newInt(0), true);
        Assert.assertEquals("(-(i)) >= 0", iLe0.toString());
        Value iLe3 = GreaterThanZeroValue.less(minimalEvaluationContext, i, newInt(3), true);
        Assert.assertEquals("(3 + (-(i))) >= 0", iLe3.toString());
        Value and = newAndAppend(iLe0, iLe3);
        Assert.assertEquals(iLe0, and);
    }

    @Test
    public void test3() {
        Value iLe0 = GreaterThanZeroValue.less(minimalEvaluationContext, i, newInt(0), true);
        Assert.assertEquals("(-(i)) >= 0", iLe0.toString());
        Value iGe3 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(3), true);
        Assert.assertEquals("((-3) + i) >= 0", iGe3.toString());
        Value and = newAndAppend(iLe0, iGe3);
        Assert.assertEquals(FALSE, and);
        Value and2 = newAndAppend(iGe3, iLe0);
        Assert.assertEquals(FALSE, and2);
    }

    @Test
    public void test4() {
        Value iGe0 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(0), true);
        Assert.assertEquals("i >= 0", iGe0.toString());
        Value iLe3 = GreaterThanZeroValue.less(minimalEvaluationContext, i, newInt(3), true);
        Assert.assertEquals("(3 + (-(i))) >= 0", iLe3.toString());
        Value and = newAndAppend(iGe0, iLe3);
        Assert.assertTrue(and instanceof AndValue);
        Value and2 = newAndAppend(iLe3, iGe0);
        Assert.assertTrue(and2 instanceof AndValue);
    }

    @Test
    public void test5() {
        Value iGe0 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(0), true);
        Assert.assertEquals("i >= 0", iGe0.toString());
        Value iGt0 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(0), false);
        Assert.assertEquals("((-1) + i) >= 0", iGt0.toString());
        Value and = newAndAppend(iGe0, iGt0);
        Assert.assertEquals(iGt0, and);
    }

    @Test
    public void test6() {
        Value iGe0 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(0), true);
        Assert.assertEquals("i >= 0", iGe0.toString());
        Value iLe0 = GreaterThanZeroValue.less(minimalEvaluationContext, i, newInt(0), true);
        Assert.assertEquals("(-(i)) >= 0", iLe0.toString());
        Value and = newAndAppend(iGe0, iLe0);
        Assert.assertEquals("0 == i", and.toString());
    }

    @Test
    public void test7() {
        Value iGe0 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(0), false);
        Assert.assertEquals("((-1) + i) >= 0", iGe0.toString());
        Value iLe0 = GreaterThanZeroValue.less(minimalEvaluationContext, i, newInt(0), false);
        Assert.assertEquals("((-1) + (-(i))) >= 0", iLe0.toString());
        Value and = newAndAppend(iGe0, iLe0);
        Assert.assertEquals(FALSE, and);
        Value and2 = newAndAppend(iLe0, iGe0);
        Assert.assertEquals(FALSE, and2);
    }

    @Test
    public void testGEZeroLZero() {
        // (((-1) + (-this.i)) >= 0 and this.i >= 0): if this fails, the problem is that this.i != this.i
        Value iLt0 = GreaterThanZeroValue.less(minimalEvaluationContext, i, newInt(0), false);
        Assert.assertEquals("((-1) + (-(i))) >= 0", iLt0.toString());
        Value iGe0 = GreaterThanZeroValue.greater(minimalEvaluationContext, i, newInt(0), true);
        Assert.assertEquals("i >= 0", iGe0.toString());

        Assert.assertEquals("false", newAndAppend(iLt0, iGe0).toString());
        Assert.assertEquals("false", newAndAppend(iGe0, iLt0).toString());
    }
}
