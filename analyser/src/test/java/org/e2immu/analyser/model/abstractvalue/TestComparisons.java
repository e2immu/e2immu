package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.IntValue;
import org.junit.Assert;
import org.junit.Test;

public class TestComparisons extends CommonAbstractValue {

    @Test
    public void testEquals() {
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
    public void test1() {
        Value iGe0 = GreaterThanZeroValue.greater(i, new IntValue(0), true);
        Assert.assertEquals("i >= 0", iGe0.toString());
        Value iGt0 = GreaterThanZeroValue.greater(i, new IntValue(0), false);
        Assert.assertEquals("i > 0", iGt0.toString());
        Value and = new AndValue().append(iGe0, iGt0);
        Assert.assertEquals(iGt0, and);
    }
}
