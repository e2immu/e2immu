package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class Loops_13 {
    // variant on Loops_12, Loops_0

    @Constant("abc")
    @NotNull
    public static String method(int n) {
        String res1 = "x"; // assignment still necessary according to compiler
        int i = 0;
        while ((i=3)>0) {
            res1 = "abc";
            i++;
            if (i >= n) break;
        }
        return res1;
    }

}
