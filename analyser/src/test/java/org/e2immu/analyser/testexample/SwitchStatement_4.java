package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class SwitchStatement_4 {

    @NotNull
    @Constant(absent = true)
    public static String method(char c) {
        String res;
        switch (c) {
            case 'a':
                res = "a";
                break;
            case 'b':
                res = "b";
                break;
            default:
                res = "c";
        }
        return res;
    }

}

