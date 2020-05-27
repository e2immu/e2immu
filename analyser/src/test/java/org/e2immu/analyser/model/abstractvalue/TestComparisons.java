package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.IntValue;
import org.junit.Assert;
import org.junit.Test;

public class TestComparisons extends CommonAbstractValue {

    @Test
    public void testNegate1() {
        GreaterThanZeroValue gt3 = (GreaterThanZeroValue) GreaterThanZeroValue.greater(i, new IntValue(3), false);
        Assert.assertEquals("((-4) + i) >= 0", gt3.toString()); // i >= 4
        GreaterThanZeroValue notGt3 = (GreaterThanZeroValue) gt3.negate();
        Assert.assertEquals("(3 + not (i)) >= 0", notGt3.toString()); // i <= 3
    }

    @Test
    public void testXb() {
        GreaterThanZeroValue gt3 = (GreaterThanZeroValue) GreaterThanZeroValue.greater(i, new IntValue(3), false);
        Assert.assertEquals("((-4) + i) >= 0", gt3.toString());
        GreaterThanZeroValue.XB xb = gt3.extract();
        Assert.assertNotNull(xb);
        Assert.assertTrue(gt3.allowEquals);
        Assert.assertEquals(4, (int) xb.b);
        Assert.assertEquals(i, xb.x);
        Assert.assertFalse(xb.lessThan);
    }

    @Test
    public void testXb2() {
        GreaterThanZeroValue lt3 = (GreaterThanZeroValue) GreaterThanZeroValue.less(i, new IntValue(3), false);
        Assert.assertEquals("(2 + not (i)) >= 0", lt3.toString());
        GreaterThanZeroValue.XB xb = lt3.extract();
        Assert.assertNotNull(xb);
        Assert.assertTrue(lt3.allowEquals);
        Assert.assertEquals(2, (int) xb.b);
        Assert.assertEquals(i, xb.x);
        Assert.assertTrue(xb.lessThan);
    }

    @Test
    public void testEqualsEquals() {
        Value iEq4 = EqualsValue.equals(i, new IntValue(4));
        Value iEq3 = EqualsValue.equals(new IntValue(3), i);
        Value and = new AndValue().append(iEq3, iEq4);
        Assert.assertEquals(BoolValue.FALSE, and);
    }

    @Test
    public void testEqualsNotEquals() {
        Value iEq4 = EqualsValue.equals(i, new IntValue(4));
        Value iEq3 = NegatedValue.negate(EqualsValue.equals(new IntValue(3), i));
        Value and = new AndValue().append(iEq3, iEq4);
        Assert.assertEquals(iEq4, and);
    }

    @Test
    public void testEqualsGreaterThan0() {
        Value iEq4 = EqualsValue.equals(i, new IntValue(4));
        Value iGe0 = GreaterThanZeroValue.greater(i, new IntValue(0), true);
        Value and = new AndValue().append(iGe0, iEq4);
        Assert.assertEquals(iEq4, and);

        Value iEqMinus4 = EqualsValue.equals(i, new IntValue(-4));
        Value and2 = new AndValue().append(iGe0, iEqMinus4);
        Assert.assertEquals(BoolValue.FALSE, and2);

        Value iEq0 = EqualsValue.equals(i, new IntValue(0));
        Value and4 = new AndValue().append(iGe0, iEq0);
        Assert.assertEquals(iEq0, and4);
    }

    // GE1 follows a different path from GE0, since -1 + x >= 0 is involved
    @Test
    public void testEqualsGreaterThan1() {
        Value iEq4 = EqualsValue.equals(i, new IntValue(4));
        Value iGe1 = GreaterThanZeroValue.greater(i, new IntValue(1), true);
        Value and = new AndValue().append(iGe1, iEq4);
        Assert.assertEquals(iEq4, and);

        Value iEqMinus4 = EqualsValue.equals(i, new IntValue(-4));
        Value and2 = new AndValue().append(iGe1, iEqMinus4);
        Assert.assertEquals(BoolValue.FALSE, and2);

        Value iEq0 = EqualsValue.equals(i, new IntValue(0));
        Value iGt0 = GreaterThanZeroValue.greater(i, new IntValue(0), false);
        Value and3 = new AndValue().append(iGt0, iEq0);
        Assert.assertEquals(BoolValue.FALSE, and3);
    }

    // GE1 follows a different path from GE0, since -1 + x >= 0 is involved
    @Test
    public void testEqualsLessThan1() {
        Value iEq4 = EqualsValue.equals(i, new IntValue(4));
        Value iLe1 = GreaterThanZeroValue.less(i, new IntValue(1), true);
        Value and = new AndValue().append(iLe1, iEq4);
        Assert.assertEquals(BoolValue.FALSE, and);

        Value iEqMinus4 = EqualsValue.equals(i, new IntValue(-4));
        Value and2 = new AndValue().append(iLe1, iEqMinus4);
        Assert.assertEquals(iEqMinus4, and2);

        Value iEq0 = EqualsValue.equals(i, new IntValue(0));
        Value iLt0 = GreaterThanZeroValue.less(i, new IntValue(0), false);
        Value and3 = new AndValue().append(iLt0, iEq0);
        Assert.assertEquals(BoolValue.FALSE, and3);
    }

    @Test
    public void test1() {
        Value iGe0 = GreaterThanZeroValue.greater(i, new IntValue(0), true);
        Assert.assertEquals("i >= 0", iGe0.toString());
        Value iGe3 = GreaterThanZeroValue.greater(i, new IntValue(3), true);
        Assert.assertEquals("((-3) + i) >= 0", iGe3.toString());
        Value and = new AndValue().append(iGe0, iGe3);
        Assert.assertEquals(iGe3, and);
        Value and2 = new AndValue().append(iGe3, iGe0);
        Assert.assertEquals(iGe3, and2);
    }

    // i <= 0 && i <= 3 ==> i <= 0
    @Test
    public void test2() {
        Value iLe0 = GreaterThanZeroValue.less(i, new IntValue(0), true);
        Assert.assertEquals("not (i) >= 0", iLe0.toString());
        Value iLe3 = GreaterThanZeroValue.less(i, new IntValue(3), true);
        Assert.assertEquals("(3 + not (i)) >= 0", iLe3.toString());
        Value and = new AndValue().append(iLe0, iLe3);
        Assert.assertEquals(iLe0, and);
    }

    @Test
    public void test3() {
        Value iLe0 = GreaterThanZeroValue.less(i, new IntValue(0), true);
        Assert.assertEquals("not (i) >= 0", iLe0.toString());
        Value iGe3 = GreaterThanZeroValue.greater(i, new IntValue(3), true);
        Assert.assertEquals("((-3) + i) >= 0", iGe3.toString());
        Value and = new AndValue().append(iLe0, iGe3);
        Assert.assertEquals(BoolValue.FALSE, and);
        Value and2 = new AndValue().append(iGe3, iLe0);
        Assert.assertEquals(BoolValue.FALSE, and2);
    }

    @Test
    public void test4() {
        Value iGe0 = GreaterThanZeroValue.greater(i, new IntValue(0), true);
        Assert.assertEquals("i >= 0", iGe0.toString());
        Value iLe3 = GreaterThanZeroValue.less(i, new IntValue(3), true);
        Assert.assertEquals("(3 + not (i)) >= 0", iLe3.toString());
        Value and = new AndValue().append(iGe0, iLe3);
        Assert.assertTrue(and instanceof AndValue);
        Value and2 = new AndValue().append(iLe3, iGe0);
        Assert.assertTrue(and2 instanceof AndValue);
    }

    @Test
    public void test5() {
        Value iGe0 = GreaterThanZeroValue.greater(i, new IntValue(0), true);
        Assert.assertEquals("i >= 0", iGe0.toString());
        Value iGt0 = GreaterThanZeroValue.greater(i, new IntValue(0), false);
        Assert.assertEquals("((-1) + i) >= 0", iGt0.toString());
        Value and = new AndValue().append(iGe0, iGt0);
        Assert.assertEquals(iGt0, and);
    }

    @Test
    public void test6() {
        Value iGe0 = GreaterThanZeroValue.greater(i, new IntValue(0), true);
        Assert.assertEquals("i >= 0", iGe0.toString());
        Value iLe0 = GreaterThanZeroValue.less(i, new IntValue(0), true);
        Assert.assertEquals("not (i) >= 0", iLe0.toString());
        Value and = new AndValue().append(iGe0, iLe0);
        Assert.assertEquals("0 == i", and.toString());
    }

    @Test
    public void test7() {
        Value iGe0 = GreaterThanZeroValue.greater(i, new IntValue(0), false);
        Assert.assertEquals("((-1) + i) >= 0", iGe0.toString());
        Value iLe0 = GreaterThanZeroValue.less(i, new IntValue(0), false);
        Assert.assertEquals("((-1) + not (i)) >= 0", iLe0.toString());
        Value and = new AndValue().append(iGe0, iLe0);
        Assert.assertEquals(BoolValue.FALSE, and);
        Value and2 = new AndValue().append(iLe0, iGe0);
        Assert.assertEquals(BoolValue.FALSE, and2);
    }

    @Test
    public void testSizeRestriction() {
        Value iGe0 = GreaterThanZeroValue.greater(i, new IntValue(0), false);
        Assert.assertEquals(Analysis.SIZE_NOT_EMPTY, iGe0.encodedSizeRestriction());
        Value iGe3 = GreaterThanZeroValue.greater(i, new IntValue(3), true);
        Assert.assertEquals(Analysis.encodeSizeMin(3), iGe3.encodedSizeRestriction());
        Value iEq4 = EqualsValue.equals(i, new IntValue(4));
        Assert.assertEquals(Analysis.encodeSizeEquals(4), iEq4.encodedSizeRestriction());
    }
}
