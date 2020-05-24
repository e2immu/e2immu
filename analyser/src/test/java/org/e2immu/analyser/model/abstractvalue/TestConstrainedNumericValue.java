package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.parser.Primitives;
import org.junit.Assert;
import org.junit.Test;

public class TestConstrainedNumericValue {

    // this one tests the 3 different forms of negation for ">= 1"
    @Test
    public void test() {
        ConstrainedNumericValue ge1 = ConstrainedNumericValue.lowerBound(Primitives.PRIMITIVES.intParameterizedType, 1, true);
        Value minusGe1 = NegatedValue.negate(ge1, false);
        Assert.assertTrue(minusGe1 instanceof ConstrainedNumericValue);
        ConstrainedNumericValue minusGe1Cnv = (ConstrainedNumericValue) minusGe1;
        Assert.assertEquals(-1.0d, minusGe1Cnv.upperBound, 0.0001);
        Assert.assertEquals("?<=-1", minusGe1Cnv.asString());

        Value notGe1 = NegatedValue.negate(ge1, true);
        Assert.assertTrue(notGe1 instanceof ConstrainedNumericValue);
        ConstrainedNumericValue notGe1Cnv = (ConstrainedNumericValue) notGe1;
        Assert.assertEquals(1.0d, notGe1Cnv.upperBound, 0.0001);
        Assert.assertEquals("?<1", notGe1Cnv.asString());

        Value pvNotGe1 = ge1.booleanNegatedValue(true);
        Assert.assertEquals("-1<?<1", pvNotGe1.asString());
    }

    @Test
    public void testGe2() {
        ConstrainedNumericValue ge2 = ConstrainedNumericValue.lowerBound(Primitives.PRIMITIVES.intParameterizedType, 2, true);
        Value pvNotGe2 = ge2.booleanNegatedValue(true);
        Assert.assertEquals("-1<?<2", pvNotGe2.asString());
    }

    @Test
    public void testGt2() {
        ConstrainedNumericValue ge2 = ConstrainedNumericValue.lowerBound(Primitives.PRIMITIVES.intParameterizedType, 2, false);
        Value pvNotGe2 = ge2.booleanNegatedValue(true);
        Assert.assertEquals("0<=?<=2", pvNotGe2.asString());
    }
}
