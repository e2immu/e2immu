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
        Assert.assertNull(sEqualsNull.nonIndividualCondition());

        Value tEqualsNull = EqualsValue.equals(NullValue.NULL_VALUE, t);
        Assert.assertEquals("null == t", tEqualsNull.toString());
        Assert.assertNull(tEqualsNull.nonIndividualCondition());
        Assert.assertNull(NegatedValue.negate(tEqualsNull).nonIndividualCondition());

        Value orValue = new OrValue().append(sEqualsNull, tEqualsNull);
        Assert.assertNull(orValue.nonIndividualCondition());

        Value andValue = new AndValue().append(sEqualsNull, tEqualsNull);
        Assert.assertSame(andValue, andValue.nonIndividualCondition());

        Value notAndValue = NegatedValue.negate(andValue);
        Assert.assertNull(notAndValue.nonIndividualCondition());
    }
}
