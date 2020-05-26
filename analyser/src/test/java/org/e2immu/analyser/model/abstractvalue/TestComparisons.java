package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.IntValue;
import org.junit.Assert;
import org.junit.Test;

public class TestComparisons extends CommonAbstractValue {

    @Test
    public void testXb() {
        GreaterThanZeroValue gt3 = (GreaterThanZeroValue) GreaterThanZeroValue.greater(i, new IntValue(3), false);
        Assert.assertEquals("((-3) + i) > 0", gt3.toString());
        GreaterThanZeroValue.XB xb = gt3.extract();
        Assert.assertNotNull(xb);
        Assert.assertEquals(3, (int) xb.b);
        Assert.assertEquals(i, xb.x);
        Assert.assertFalse(xb.lessThan);
    }

    @Test
    public void testXb2() {
        GreaterThanZeroValue gt3 = (GreaterThanZeroValue) GreaterThanZeroValue.less(i, new IntValue(3), false);
        Assert.assertEquals("(3 + not (i)) > 0", gt3.toString());
        GreaterThanZeroValue.XB xb = gt3.extract();
        Assert.assertNotNull(xb);
        Assert.assertEquals(3, (int) xb.b);
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
        Value iEq3 = NegatedValue.negate(EqualsValue.equals(new IntValue(3), i), true);
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
        Value iGt0 = GreaterThanZeroValue.greater(i, new IntValue(0), false);
        Assert.assertEquals("i > 0", iGt0.toString());
        Value and = new AndValue().append(iGe0, iGt0);
        Assert.assertEquals(iGt0, and);
    }
}
