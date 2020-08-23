package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.NullValue;
import org.junit.Assert;
import org.junit.Test;

public class TestNonIndividualCondition extends CommonAbstractValue {

    private Value rest(Value value, boolean preconditionSide) {
        return value.filter(preconditionSide, Value::isIndividualNotNullClauseOnParameter, Value::isIndividualSizeRestrictionOnParameter).rest;
    }

    @Test
    public void test1() {
        Value sEqualsNull = EqualsValue.equals(NullValue.NULL_VALUE, s);
        Assert.assertEquals("null == s", sEqualsNull.toString());
        Assert.assertEquals("null == s", rest(sEqualsNull, true).toString());

        Value pEqualsNull = EqualsValue.equals(NullValue.NULL_VALUE, p);
        Assert.assertEquals("null == p", pEqualsNull.toString());
        Assert.assertSame(UnknownValue.NO_VALUE, rest(pEqualsNull, true));
        Assert.assertSame(UnknownValue.NO_VALUE, rest(NegatedValue.negate(pEqualsNull), true));

        Value orValue = new OrValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("null == s", rest(orValue, false).toString());

        Value orValue2 = new OrValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("(null == p or null == s)", rest(orValue2, true).toString());

        Value andValue = new AndValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("(null == p and null == s)", rest(andValue, false).toString());

        Value andValue2 = new AndValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("null == s", rest(andValue, true).toString());

        Value notAndValue = NegatedValue.negate(andValue);
        Assert.assertEquals("(not (null == p) or not (null == s))", notAndValue.toString());
        Assert.assertEquals("not (null == s)", rest(notAndValue, false).toString());
    }
}
