package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class SwitchStatement_1 {

    @NotNull(absent = true)
    @Constant(absent = true)
    public static String method(char c, String b) {
        switch (c) {
            case 'a':
                return "a";
            case 'b':
                return "b";
            default:
                return b;
        }
    }

}

