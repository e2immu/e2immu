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
        Assert.assertEquals("null == s", sEqualsNull.nonIndividualCondition().toString());

        Value pEqualsNull = EqualsValue.equals(NullValue.NULL_VALUE, p);
        Assert.assertEquals("null == p", pEqualsNull.toString());
        Assert.assertNull(pEqualsNull.nonIndividualCondition());
        Assert.assertNull(NegatedValue.negate(pEqualsNull).nonIndividualCondition());

        Value orValue = new OrValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("null == s", orValue.nonIndividualCondition().toString());

        Value andValue = new AndValue().append(sEqualsNull, pEqualsNull);
        Assert.assertSame(andValue, andValue.nonIndividualCondition());

        Value notAndValue = NegatedValue.negate(andValue);
        Assert.assertEquals("not (null == s)", notAndValue.nonIndividualCondition().toString());
    }
}
