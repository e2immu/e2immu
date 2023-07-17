package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.GreaterThanZero;
import org.e2immu.analyser.model.expression.NullConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestAndComparison extends CommonAbstractValue {

    /*
    important, even though vine is nullable, it is of type int
     */
    @Test
    public void test1() {
        Expression inIsNull = equals(vine, NullConstant.NULL_CONSTANT);
        assertEquals("null==in", inIsNull.toString());
        Expression inIsNotNull = negate(inIsNull);
        assertEquals("null!=in", inIsNotNull.toString());
        Expression inGE0 = GreaterThanZero.greater(context, vine, newInt(0), true);
        assertEquals("in>=0", inGE0.toString());
        Expression inLT0 = GreaterThanZero.less(context, vine, newInt(0), false);
        assertEquals("in<0", inLT0.toString());

        Expression and1 = newAnd(inIsNotNull, inGE0);
        Expression and2 = newAnd(inIsNotNull, inLT0);
        Expression and = newAndAppend(and1, and2);
        assertEquals("false", and.toString());
    }

    // now double
    @Test
    public void test2() {
        Expression lGE0 = GreaterThanZero.greater(context, l, newInt(0), true);
        assertEquals("l>=0", lGE0.toString());
        Expression lLT0 = GreaterThanZero.less(context, l, newInt(0), false);
        assertEquals("l<0", lLT0.toString());
        Expression and = newAndAppend(lGE0, lLT0);
        assertEquals("false", and.toString());
    }
}
