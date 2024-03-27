package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.HiddenContent;
import org.e2immu.analyser.model.ParameterizedType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestHiddenContent extends CommonTest {

    @Test
    @DisplayName("arrays of type parameters")
    public void test() {
        ParameterizedType tpArray = new ParameterizedType(tp0, 1, ParameterizedType.WildCard.NONE);
        assertEquals("Type param T[]", tpArray.toString());
        HiddenContent hc1 = HiddenContent.from(tpArray);
        assertEquals("<0>", hc1.toString());
        assertEquals("<0>", hc1.selectAll().toString());

        ParameterizedType tpArray2 = new ParameterizedType(tp0, 2, ParameterizedType.WildCard.NONE);
        assertEquals("Type param T[][]", tpArray2.toString());
        HiddenContent hc2 = HiddenContent.from(tpArray2);
        assertEquals("<*0-0>", hc2.toString());
        assertEquals("<0>", hc2.selectAll().toString());
    }

    @Test
    @DisplayName("arrays of non-hidden content types")
    public void test2() {
        ParameterizedType intArray = new ParameterizedType(primitives.intTypeInfo(), 2);
        assertEquals("Type int[][]", intArray.toString());
        HiddenContent hc1 = HiddenContent.from(intArray);
        assertEquals("<*0-*0>", hc1.toString());
        assertTrue(hc1.selectAll().isNone());

        ParameterizedType mutableOfStringArray = new ParameterizedType(mutableWithOneTypeParameter,
                List.of(primitives.stringParameterizedType()), 1);
        assertEquals("Type com.foo.MutableTP<String>[]", mutableOfStringArray.toString());
        HiddenContent hc2 = HiddenContent.from(mutableOfStringArray);
        assertEquals("<*0-*0>", hc2.toString());
        assertTrue(hc2.selectAll().isNone());

        // completely irrelevant whether we use MutableTP or ImmutableHCTP
        ParameterizedType immutableHcOfStringArray = new ParameterizedType(immutableHcWithOneTypeParameter,
                List.of(primitives.stringParameterizedType()), 1);
        assertEquals("Type com.foo.ImmutableHcTP<String>[]", immutableHcOfStringArray.toString());
        HiddenContent hc3 = HiddenContent.from(immutableHcOfStringArray);
        assertEquals("<*0-*0>", hc3.toString());
        assertTrue(hc3.selectAll().isNone());
    }
}
