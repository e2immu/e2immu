package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.ParameterizedType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestLv extends CommonTest {

    @Test
    public void test(){
        ParameterizedType tpArray = new ParameterizedType(tp0, 1, ParameterizedType.WildCard.NONE);
        assertEquals("Type param T[]", tpArray.toString());
        assertEquals("<0>", LV.from(tpArray).toString());

        ParameterizedType tpArray2 = new ParameterizedType(tp0, 2, ParameterizedType.WildCard.NONE);
        assertEquals("Type param T[][]", tpArray2.toString());
        assertEquals("<*0-0>", LV.from(tpArray2).toString());
    }
}
