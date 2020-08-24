package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.NullValue;
import org.junit.Assert;
import org.junit.Test;

public class TestNonIndividualCondition extends CommonAbstractValue {

    private Value rest(Value value, Value.FilterMode filterMode) {
        return value.filter(filterMode, Value::isIndividualNotNullClauseOnParameter, Value::isIndividualSizeRestrictionOnParameter).rest;
    }

    @Test
    public void test1() {
        Value sEqualsNull = EqualsValue.equals(NullValue.NULL_VALUE, s);
        Assert.assertEquals("null == s", sEqualsNull.toString());
        Assert.assertEquals("null == s", rest(sEqualsNull, Value.FilterMode.ACCEPT).toString());

        Value pEqualsNull = EqualsValue.equals(NullValue.NULL_VALUE, p);
        Assert.assertEquals("null == p", pEqualsNull.toString());
        Assert.assertSame(UnknownValue.EMPTY, rest(pEqualsNull, Value.FilterMode.ACCEPT));
        Assert.assertSame(UnknownValue.EMPTY, rest(NegatedValue.negate(pEqualsNull), Value.FilterMode.ACCEPT));

        Value orValue = new OrValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("null == s", rest(orValue, Value.FilterMode.REJECT).toString());

        Value orValue2 = new OrValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("(null == p or null == s)", rest(orValue2, Value.FilterMode.ACCEPT).toString());

        Value andValue = new AndValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("(null == p and null == s)", rest(andValue, Value.FilterMode.REJECT).toString());

        Value andValue2 = new AndValue().append(sEqualsNull, pEqualsNull);
        Assert.assertEquals("null == s", rest(andValue, Value.FilterMode.ACCEPT).toString());

        Value notAndValue = NegatedValue.negate(andValue);
        Assert.assertEquals("(not (null == p) or not (null == s))", notAndValue.toString());
        Assert.assertEquals("not (null == s)", rest(notAndValue, Value.FilterMode.REJECT).toString());
    }
}
