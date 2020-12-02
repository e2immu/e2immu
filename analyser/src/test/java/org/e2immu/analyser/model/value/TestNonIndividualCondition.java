package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.output.PrintMode;
import org.junit.Assert;
import org.junit.Test;

public class TestNonIndividualCondition extends CommonAbstractValue {

    private Value rest(Value value, Filter.FilterMode filterMode) {
        return Filter.filter(minimalEvaluationContext, value,
                filterMode, Filter.INDIVIDUAL_NULL_OR_NOT_NULL_CLAUSE_ON_PARAMETER).rest();
    }

    @Test
    public void test1() {
        Value sEqualsNull = equals(NullValue.NULL_VALUE, s);
        Assert.assertEquals("null == s", sEqualsNull.toString());
        Assert.assertEquals("null == s", rest(sEqualsNull, Filter.FilterMode.ACCEPT).toString());

        Value pEqualsNull = equals(NullValue.NULL_VALUE, p);
        Assert.assertEquals("null == some.type.type(String):0:p", pEqualsNull.toString());
        Assert.assertEquals("null == p", pEqualsNull.print(PrintMode.FOR_ANNOTATIONS));
        Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, rest(pEqualsNull, Filter.FilterMode.ACCEPT));
        Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, rest(negate(pEqualsNull), Filter.FilterMode.ACCEPT));

        Value orValue = newOrAppend(sEqualsNull, pEqualsNull);
        Assert.assertEquals("null == s", rest(orValue, Filter.FilterMode.REJECT).toString());

        Value orValue2 = newOrAppend(sEqualsNull, pEqualsNull);
        Assert.assertEquals("(null == s or null == some.type.type(String):0:p)", rest(orValue2, Filter.FilterMode.ACCEPT).toString());
        Assert.assertEquals("(null == s or null == p)", rest(orValue2, Filter.FilterMode.ACCEPT).print(PrintMode.FOR_ANNOTATIONS));

        Value andValue = newAndAppend(sEqualsNull, pEqualsNull);
        Assert.assertEquals("(null == s and null == some.type.type(String):0:p)", rest(andValue, Filter.FilterMode.REJECT).toString());

        Value andValue2 = newAndAppend(sEqualsNull, pEqualsNull);
        Assert.assertEquals("null == s", rest(andValue2, Filter.FilterMode.ACCEPT).toString());

        Value notAndValue = negate(andValue);
        Assert.assertEquals("(not (null == s) or not (null == some.type.type(String):0:p))", notAndValue.toString());
        Assert.assertEquals("not (null == s)", rest(notAndValue, Filter.FilterMode.REJECT).toString());
    }
}
