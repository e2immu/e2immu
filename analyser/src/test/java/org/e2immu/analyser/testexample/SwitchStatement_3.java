package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class SwitchStatement_3 {

    @NotNull(absent = true)
    @Constant(absent = true)
    public static String method(char c, String b) {
        switch (c) {
            default:
        }
        return b;
    }

}

