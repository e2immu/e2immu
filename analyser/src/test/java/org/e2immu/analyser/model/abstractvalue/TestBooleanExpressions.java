package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;
import org.junit.Assert;
import org.junit.Test;

public class TestBooleanExpressions extends CommonAbstractValue {

    /* the following test simulates

        if(a && b) return 1;
        if(!a && !b) return 2;
        // where are we here?

     which is identical to

        if(a && b  || !a && !b) return;
        // where are we here?
     */
    @Test
    public void test1() {
        Value aAndB = new AndValue().append(a, b);
        Assert.assertEquals("(a and b)", aAndB.toString());
        Value not_AAndB = NegatedValue.negate(aAndB);
        Assert.assertEquals("(not (a) or not (b))", not_AAndB.toString());

        Value notAAndNotB = new AndValue().append(NegatedValue.negate(a), NegatedValue.negate(b));
        Assert.assertEquals("(not (a) and not (b))", notAAndNotB.toString());
        Value notNotAAndNotB = NegatedValue.negate(notAAndNotB);
        Assert.assertEquals("(a or b)", notNotAAndNotB.toString());

        // then we combine these two
        Value n1AndN2 = new AndValue().append(not_AAndB, notNotAAndNotB);
        Assert.assertEquals("((a or b) and (not (a) or not (b)))", n1AndN2.toString());

        Value notA_andB = new AndValue().append(NegatedValue.negate(a), b);
        Value bOrNotA = NegatedValue.negate(notA_andB);
        Assert.assertEquals("(a or not (b))", bOrNotA.toString());

        Value n1AndN2AndN3 = new AndValue().append(n1AndN2, bOrNotA);
        Assert.assertEquals("(a and not (b))", n1AndN2AndN3.toString());
    }
}
