package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.NullConstant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAndSorting extends CommonAbstractValue {

    // null checks go to the front
    @Test
    public void test1() {
        Expression sIsNotNull = negate(equals(s, NullConstant.NULL_CONSTANT));
        assertEquals("null!=s", sIsNotNull.toString());
        Expression sLength = new MethodCall(Identifier.CONSTANT, s, PRIMITIVES.andOperatorBool(), List.of());
        assertEquals("s.&&()", sLength.toString());
        Expression a1 = newAndAppend(sIsNotNull, sLength);
        assertEquals("null!=s&&s.&&()", a1.toString());
        Expression a2 = newAndAppend(sLength, sIsNotNull);
        assertEquals("null!=s&&s.&&()", a2.toString());
    }

    // method calls remain in the same order
    @Test
    public void test2() {
        Expression sIsNotNull = negate(equals(s, NullConstant.NULL_CONSTANT));
        assertEquals("null!=s", sIsNotNull.toString());
        Expression s1 = new MethodCall(Identifier.CONSTANT, s, PRIMITIVES.andOperatorBool(), List.of());
        assertEquals("s.&&()", s1.toString());
        Expression s2 = new MethodCall(Identifier.CONSTANT, s, PRIMITIVES.orOperatorBool(), List.of());
        assertEquals("s.||()", s2.toString());

        Expression a1 = newAndAppend(sIsNotNull, s1, s2);
        assertEquals("null!=s&&s.&&()&&s.||()", a1.toString());
        Expression a2 = newAndAppend(s2, sIsNotNull, s1);
        assertEquals("null!=s&&s.||()&&s.&&()", a2.toString());
    }
}
