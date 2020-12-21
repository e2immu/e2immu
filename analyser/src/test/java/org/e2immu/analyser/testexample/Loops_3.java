package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class Loops_3 {

    @Constant("a")
    @NotNull
    public static String method() {
        String res = "a";
        for (String s : new String[]{}) {
            res = s;
        }
        return res;
    }

}
