package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.parser.Primitives;
import org.junit.Assert;
import org.junit.Test;

public class TestConstrainedNumericValue {

    // this one tests the 3 different forms of negation for ">= 1"
    @Test
    public void test() {
        ConstrainedNumericValue ge1 = ConstrainedNumericValue.intLowerBound(1, true);
        Value minusGe1 = NegatedValue.negate(ge1, false);
        Assert.assertTrue(minusGe1 instanceof ConstrainedNumericValue);
        ConstrainedNumericValue minusGe1Cnv = (ConstrainedNumericValue) minusGe1;
        Assert.assertEquals(-1.0d, minusGe1Cnv.upperBound, 0.0001);
        Assert.assertEquals("?<=-1", minusGe1Cnv.toString());

        Value notGe1 = NegatedValue.negate(ge1, true);
        Assert.assertTrue(notGe1 instanceof ConstrainedNumericValue);
        ConstrainedNumericValue notGe1Cnv = (ConstrainedNumericValue) notGe1;
        Assert.assertEquals(1.0d, notGe1Cnv.upperBound, 0.0001);
        Assert.assertEquals("?<1", notGe1Cnv.toString());

        Value pvNotGe1 = ge1.booleanNegatedValue(true);
        Assert.assertEquals("-1<?<1", pvNotGe1.toString());
    }

    @Test
    public void testGe2() {
        ConstrainedNumericValue ge2 = ConstrainedNumericValue.intLowerBound(2, true);
        Value pvNotGe2 = ge2.booleanNegatedValue(true);
        Assert.assertEquals("-1<?<2", pvNotGe2.toString());
    }

    @Test
    public void testGt2() {
        ConstrainedNumericValue ge2 = ConstrainedNumericValue.intLowerBound(2, false);
        Value pvNotGe2 = ge2.booleanNegatedValue(true);
        Assert.assertEquals("0<=?<=2", pvNotGe2.toString());
    }

    @Test
    public void testEquals0() {
        ConstrainedNumericValue ge0 = ConstrainedNumericValue.intLowerBound(0, true);
        Value eqGe0 = EqualsValue.equals(ge0, new IntValue(0));
        Assert.assertTrue(eqGe0 instanceof EqualsValue);
        Assert.assertEquals("0 == ?>=0", eqGe0.toString());

        // now take the boolean complement
        Value notEqGe0 = NegatedValue.negate(eqGe0, true);
        Assert.assertEquals("?>0", notEqGe0.toString());
    }

    @Test
    public void testEquals1() {
        ConstrainedNumericValue gt1 = ConstrainedNumericValue.intLowerBound(1, false);
        Value eqGt1 = EqualsValue.equals(gt1, new IntValue(2));
        Assert.assertTrue(eqGt1 instanceof EqualsValue);
        Assert.assertEquals("2 == ?>1", eqGt1.toString());

        // now take the boolean complement
        Value notEqGt1 = NegatedValue.negate(eqGt1, true);
        Assert.assertEquals("?>2", notEqGt1.toString());
    }

    @Test
    public void testEncodeSize() {
        ConstrainedNumericValue cnv1 = ConstrainedNumericValue.intLowerBound(0, false);
        Assert.assertEquals(Analysis.SIZE_NOT_EMPTY, cnv1.sizeRestriction());

        ConstrainedNumericValue cnv2 = ConstrainedNumericValue.intLowerBound(1, true);
        Assert.assertEquals(Analysis.SIZE_NOT_EMPTY, cnv2.sizeRestriction());
    }
}
