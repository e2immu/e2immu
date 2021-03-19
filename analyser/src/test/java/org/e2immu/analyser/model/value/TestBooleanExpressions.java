package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.NullConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        Expression aAndB = newAndAppend(a, b);
        assertEquals("a&&b", aAndB.toString());
        Expression not_AAndB = negate(aAndB);
        assertEquals("!a||!b", not_AAndB.toString());

        Expression notAAndNotB = newAndAppend(negate(a), negate(b));
        assertEquals("!a&&!b", notAAndNotB.toString());
        Expression notNotAAndNotB = negate(notAAndNotB);
        assertEquals("a||b", notNotAAndNotB.toString());

        // then we combine these two
        Expression n1AndN2 = newAndAppend(not_AAndB, notNotAAndNotB);
        assertEquals("(a||b)&&(!a||!b)", n1AndN2.toString());

        Expression notA_andB = newAndAppend(negate(a), b);
        Expression bOrNotA = negate(notA_andB);
        assertEquals("a||!b", bOrNotA.toString());

        Expression n1AndN2AndN3 = newAndAppend(n1AndN2, bOrNotA);
        assertEquals("a&&!b", n1AndN2AndN3.toString());
    }

    @Test
    public void testEither() {
        Expression aAndB = newAndAppend(a, b);
        Expression notAandNotB = newAndAppend(negate(a), negate(b));
        Expression aAndB_or_notAandNotB = newOrAppend(aAndB, notAandNotB);
        assertEquals("(a||!b)&&(!a||b)", aAndB_or_notAandNotB.toString());

        Expression not__aAndB_or_notAandNotB = negate(aAndB_or_notAandNotB);
        assertEquals("(a||b)&&(!a||!b)", not__aAndB_or_notAandNotB.toString());

        Expression combined = newAndAppend(not__aAndB_or_notAandNotB, aAndB_or_notAandNotB);
        assertEquals("false", combined.toString());
        Expression combined2 = newAndAppend(aAndB_or_notAandNotB, not__aAndB_or_notAandNotB);
        assertEquals("false", combined2.toString());
    }

    @Test
    public void testEither2() {
        Expression aNull = equals(an, NullConstant.NULL_CONSTANT);
        Expression aNotNull = negate(equals(an, NullConstant.NULL_CONSTANT));
        Expression bNull = equals(bn, NullConstant.NULL_CONSTANT);
        Expression bNotNull = negate(equals(bn, NullConstant.NULL_CONSTANT));

        Expression aNullOrBNotNull = newOrAppend(aNull, bNotNull);
        assertEquals("null==an||null!=bn", aNullOrBNotNull.toString());
        Expression aNotNullOrBNull = newOrAppend(aNotNull, bNull);
        assertEquals("null!=an||null==bn", aNotNullOrBNull.toString());
        Expression and = newAndAppend(aNullOrBNotNull, aNotNullOrBNull);
        assertEquals("(null==an||null!=bn)&&(null!=an||null==bn)", and.toString());
        Expression notAnd = negate(and);
        assertEquals("(null==an||null==bn)&&(null!=an||null!=bn)", notAnd.toString());
        Expression notNotAnd = negate(notAnd);
        assertEquals("(null==an||null!=bn)&&(null!=an||null==bn)", notNotAnd.toString());
    }
}
