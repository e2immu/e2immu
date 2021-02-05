package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class SwitchExpression_0 {

    @NotNull
    @Constant(absent = true)
    public static String method(char c) {
        return switch (c) {
            case 'a' -> "a";
            case 'b' -> "b";
            default -> "c";
        };
    }

}

