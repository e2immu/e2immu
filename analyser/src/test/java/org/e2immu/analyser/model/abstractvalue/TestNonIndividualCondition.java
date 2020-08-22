package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.NullValue;
import org.junit.Assert;
import org.junit.Test;

public class TestNonIndividualCondition extends CommonAbstractValue {

    @Test
    public void test1() {
        Value sEqualsNull = EqualsValue.equals(NullValue.NULL_VALUE, s);
        Assert.assertEquals("null == s", sEqualsNull.toString());
        Assert.assertEquals("null == s", sEqualsNull.nonIndividualCondition(true, true).toString());

        Value pEqualsNull = EqualsValue.equals(NullValue.NULL_VALUE, p);
        Assert.assertEquals("null == p", pEqualsNull.toString());
        Assert.assertNull(pEqualsNull.nonIndividualCondition(true, true));
        Assert.assertNull(NegatedValue.negate(pEqualsNull).nonIndividualCondition(true, true));

        Value orValue = new OrValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("null == s", orValue.nonIndividualCondition(false, true).toString());

        Value orValue2 = new OrValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("(null == p or null == s)", orValue2.nonIndividualCondition(true, true).toString());

        Value andValue = new AndValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("(null == p and null == s)", andValue.nonIndividualCondition(false, true).toString());

        Value andValue2 = new AndValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("null == s", andValue.nonIndividualCondition(true, true).toString());

        Value notAndValue = NegatedValue.negate(andValue);
        Assert.assertEquals("(not (null == p) or not (null == s))", notAndValue.toString());
        Assert.assertEquals("not (null == s)", notAndValue.nonIndividualCondition(false, true).toString());
    }
}
