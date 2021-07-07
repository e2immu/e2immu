package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class Loops_12 {

    // variant of Loops_0 and Loops_4 where there is an assignment (and not a synthetic one) in an empty loop

    @Constant("x")
    @NotNull
    public static String method(int n) {
        String res1= "x";
        int i = 0;
        while ((i=3)<0) {
            res1 = "abc";
            i++;
            if (i >= n) break;
        }
        return res1;
    }
}
