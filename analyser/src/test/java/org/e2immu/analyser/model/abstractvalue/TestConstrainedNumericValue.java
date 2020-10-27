package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.IntValue;
import org.junit.Assert;
import org.junit.Test;

public class TestConstrainedNumericValue extends CommonAbstractValue {

    @Test
    public void test() {
        ConstrainedNumericValue ge1 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 1);
        Assert.assertEquals("i,?>=1", ge1.toString());
    }

    @Test
    public void testEquals0() {
        ConstrainedNumericValue ge0 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 0);
        Value eqGe0 = equals(ge0, new IntValue(0));
        Assert.assertTrue(eqGe0 instanceof EqualsValue);
        Assert.assertEquals("0 == i,?>=0", eqGe0.toString());

        // now take the boolean complement  NOT >=0 == >= 1
        Value notEqGe0 = NegatedValue.negate(eqGe0);
        Assert.assertEquals("((-1) + i,?>=0) >= 0", notEqGe0.toString());
    }

    @Test
    public void testEquals1() {
        ConstrainedNumericValue gt1 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 1);
        Value eqGt1 = equals(gt1, new IntValue(1));
        Assert.assertTrue(eqGt1 instanceof EqualsValue);
        Assert.assertEquals("1 == i,?>=1", eqGt1.toString());

        // now take the boolean complement
        Value notEqGt1 = NegatedValue.negate(eqGt1);
        Assert.assertEquals("((-2) + i,?>=1) >= 0", notEqGt1.toString());
    }

    @Test
    public void testEquals2() {
        ConstrainedNumericValue gt1 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 0);
        Value eqGt1 = equals(gt1, new IntValue(2));
        Assert.assertTrue(eqGt1 instanceof EqualsValue);
        Assert.assertEquals("2 == i,?>=0", eqGt1.toString());

        // now take the boolean complement
        Value notEqGt1 = NegatedValue.negate(eqGt1);
        Assert.assertEquals("not (2 == i,?>=0)", notEqGt1.toString());
    }

    @Test
    public void testEncodeSize() {
        ConstrainedNumericValue cnv1 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 1);
        Assert.assertEquals(Level.SIZE_NOT_EMPTY, cnv1.encodedSizeRestriction());
    }

    @Test
    public void testGreaterThanTautologies() {
        ConstrainedNumericValue gt0 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 0);
        Value gt0Ge0 = GreaterThanZeroValue.greater(minimalEvaluationContext, gt0, IntValue.ZERO_VALUE, true);
        Assert.assertSame(BoolValue.TRUE, gt0Ge0);

        Value gt0GtMinus1 = GreaterThanZeroValue.greater(minimalEvaluationContext, gt0, new IntValue(-1), true);
        Assert.assertSame(BoolValue.TRUE, gt0GtMinus1);

        // because discrete, this changes into i,?>=0 >= 1
        Value gt0Gt0 = GreaterThanZeroValue.greater(minimalEvaluationContext, gt0, IntValue.ZERO_VALUE, false);
        Assert.assertEquals("((-1) + i,?>=0) >= 0", gt0Gt0.toString());
    }

    @Test
    public void testSpecialEquals1() {
        ConstrainedNumericValue gt1 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 1);
        Value gt1Gt2 = GreaterThanZeroValue.greater(minimalEvaluationContext, gt1, IntValue.TWO_VALUE, true);
        Assert.assertEquals("((-2) + i,?>=1) >= 0", gt1Gt2.toString());

        ConstrainedNumericValue gt0 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 0);
        Value gt0Gt2 = GreaterThanZeroValue.greater(minimalEvaluationContext, gt0, IntValue.TWO_VALUE, true);
        Assert.assertEquals("((-2) + i,?>=0) >= 0", gt0Gt2.toString());

        Assert.assertEquals(gt0Gt2, gt1Gt2);
        Assert.assertEquals(gt1Gt2, gt0Gt2);
    }

    @Test
    public void testSpecialEquals2() {
        ConstrainedNumericValue gt1 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 1);
        Value gt1Gt2 = GreaterThanZeroValue.greater(minimalEvaluationContext, gt1, IntValue.TWO_VALUE, true);
        Assert.assertEquals("((-2) + i,?>=1) >= 0", gt1Gt2.toString());

        ConstrainedNumericValue gt0 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 0);
        Value gt0Gt3 = GreaterThanZeroValue.greater(minimalEvaluationContext, gt0, new IntValue(3), true);
        Assert.assertEquals("((-3) + i,?>=0) >= 0", gt0Gt3.toString());

        Assert.assertNotEquals(gt0Gt3, gt1Gt2);
        Assert.assertNotEquals(gt1Gt2, gt0Gt3);
    }

    @Test
    public void testCombination() {
        ConstrainedNumericValue gt1 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 1);
        Value gt1Gt2 = GreaterThanZeroValue.greater(minimalEvaluationContext, gt1, IntValue.TWO_VALUE, true);
        Assert.assertEquals("((-2) + i,?>=1) >= 0", gt1Gt2.toString());

        ConstrainedNumericValue gt0 = ConstrainedNumericValue.lowerBound(minimalEvaluationContext, i, 0);
        Value gt0Gt3 = GreaterThanZeroValue.greater(minimalEvaluationContext, gt0, new IntValue(3), true);
        Assert.assertEquals("((-3) + i,?>=0) >= 0", gt0Gt3.toString());

        Value combined = new AndValue().append(gt1Gt2, gt0Gt3);
        Assert.assertSame(gt0Gt3, combined);
    }
}
