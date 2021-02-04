package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class SwitchStatement_5 {

    // TODO this one works like test 4 at the moment, we don't have any support for
    // not having break statements... or should we block this?
    @NotNull
    @Constant(absent = true)
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

