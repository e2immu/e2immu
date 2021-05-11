package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Var_4 {

    public static Function<String, String> repeater(int i) {
        return (@NotNull var x) -> x.repeat(i);
    }

    @Test
    public void test() {
        assertEquals("yyy", repeater(3).apply("y"));
    }
}
