package org.e2immu.analyser.model.value;

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
        Value aAndB = newAndAppend(a, b);
        Assert.assertEquals("(a and b)", aAndB.toString());
        Value not_AAndB = negate(aAndB);
        Assert.assertEquals("(not (a) or not (b))", not_AAndB.toString());

        Value notAAndNotB = newAndAppend(negate(a), negate(b));
        Assert.assertEquals("(not (a) and not (b))", notAAndNotB.toString());
        Value notNotAAndNotB = negate(notAAndNotB);
        Assert.assertEquals("(a or b)", notNotAAndNotB.toString());

        // then we combine these two
        Value n1AndN2 = newAndAppend(not_AAndB, notNotAAndNotB);
        Assert.assertEquals("((a or b) and (not (a) or not (b)))", n1AndN2.toString());

        Value notA_andB = newAndAppend(negate(a), b);
        Value bOrNotA = negate(notA_andB);
        Assert.assertEquals("(a or not (b))", bOrNotA.toString());

        Value n1AndN2AndN3 = newAndAppend(n1AndN2, bOrNotA);
        Assert.assertEquals("(a and not (b))", n1AndN2AndN3.toString());
    }

    @Test
    public void testEither() {
        Value aAndB = newAndAppend(a, b);
        Value notAandNotB = newAndAppend(negate(a), negate(b));
        Value aAndB_or_notAandNotB = newOrAppend(aAndB, notAandNotB);
        Assert.assertEquals("((a or not (b)) and (not (a) or b))", aAndB_or_notAandNotB.toString());

        Value not__aAndB_or_notAandNotB = negate(aAndB_or_notAandNotB);
        Assert.assertEquals("((a or b) and (not (a) or not (b)))", not__aAndB_or_notAandNotB.toString());

        Value combined = newAndAppend(not__aAndB_or_notAandNotB, aAndB_or_notAandNotB);
        Assert.assertEquals("false", combined.toString());
        Value combined2 = newAndAppend(aAndB_or_notAandNotB, not__aAndB_or_notAandNotB);
        Assert.assertEquals("false", combined2.toString());
    }
}
