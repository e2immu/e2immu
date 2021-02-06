package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class SwitchStatement_5 {

    @NotNull
    @Constant("c")
    public static String method(char c) {
        String res;
        switch (c) {
            case 'a':
                res = "a";
            case 'b':
                res = "b";
            default:
                res = "c";
        }
        return res;
    }



}

