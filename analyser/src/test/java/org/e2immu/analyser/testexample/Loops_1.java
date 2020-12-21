package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class Loops_1 {

    @Constant(absent = true)
    @NotNull(absent = true)
    public static String method(int n) {
        String res2 = null; // = null forced upon us by compiler!
        int i = 0;
        while (true) { // executed at least once, but assignment may not be reachable
            i++;
            if (i >= n) break;
            res2 = "abc";
        }
        return res2;
    }

}
