package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class SwitchStatement_6 {

    @NotNull
    public static String method(char c) {
        String res;
        char d = 'a';
        switch (d) { // Error Trivial case
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

