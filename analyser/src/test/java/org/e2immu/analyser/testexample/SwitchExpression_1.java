package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class SwitchExpression_1 {

    // should raise a warning that the condition is always false
    @NotNull
    @Constant(absent = true)
    public static String method(char c, String b) {
        return switch (c) {
            case 'a' -> "a";
            case 'b' -> "b";
            default -> c == 'a' || c == 'b' ? b: "c";
        };
    }

}

