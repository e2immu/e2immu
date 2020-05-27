package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.IntValue;
import org.junit.Assert;
import org.junit.Test;

public class TestConstrainedNumericValue extends CommonAbstractValue {

    @Test
    public void test() {
        ConstrainedNumericValue ge1 = ConstrainedNumericValue.lowerBound(i, 1);
        Assert.assertEquals("i,?>=1", ge1.toString());
    }

    @Test
    public void testEquals0() {
        ConstrainedNumericValue ge0 = ConstrainedNumericValue.lowerBound(i, 0);
        Value eqGe0 = EqualsValue.equals(ge0, new IntValue(0));
        Assert.assertTrue(eqGe0 instanceof EqualsValue);
        Assert.assertEquals("0 == i,?>=0", eqGe0.toString());

        // now take the boolean complement  NOT >=0 == >= 1
        Value notEqGe0 = NegatedValue.negate(eqGe0);
        Assert.assertEquals("((-1) + i,?>=0) >= 0", notEqGe0.toString());
    }

    @Test
    public void testEquals1() {
        ConstrainedNumericValue gt1 = ConstrainedNumericValue.lowerBound(i, 1);
        Value eqGt1 = EqualsValue.equals(gt1, new IntValue(1));
        Assert.assertTrue(eqGt1 instanceof EqualsValue);
        Assert.assertEquals("1 == i,?>=1", eqGt1.toString());

        // now take the boolean complement
        Value notEqGt1 = NegatedValue.negate(eqGt1);
        Assert.assertEquals("((-2) + i,?>=1) >= 0", notEqGt1.toString());
    }

    @Test
    public void testEquals2() {
        ConstrainedNumericValue gt1 = ConstrainedNumericValue.lowerBound(i, 0);
        Value eqGt1 = EqualsValue.equals(gt1, new IntValue(2));
        Assert.assertTrue(eqGt1 instanceof EqualsValue);
        Assert.assertEquals("2 == i,?>=0", eqGt1.toString());

        // now take the boolean complement
        Value notEqGt1 = NegatedValue.negate(eqGt1);
        Assert.assertEquals("not (2 == i,?>=0)", notEqGt1.toString());
    }

    @Test
    public void testEncodeSize() {
        ConstrainedNumericValue cnv1 = ConstrainedNumericValue.lowerBound(i, 1);
        Assert.assertEquals(Analysis.SIZE_NOT_EMPTY, cnv1.encodedSizeRestriction());
    }
}
