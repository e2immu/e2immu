package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class Loops_0 {

    @Constant("abc")
    @NotNull
    public static String method(int n) {
        String res1;
        int i = 0;
        while (true) {
            res1 = "abc";
            i++;
            if (i >= n) break;
        }
        return res1;
    }

}
