package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class SwitchStatement_2 {

    @NotNull
    @Constant(absent = true)
    public static String method(char c, String b) {
        switch (c) {
            case 'a':
                return "a";
            case 'b':
                return "b";
            default:
                // ERROR 1: this should raise an error (if statement expression always evaluates to false)
                if (c == 'a' || c == 'b') return b;
                return "c";
        }
    }
}

