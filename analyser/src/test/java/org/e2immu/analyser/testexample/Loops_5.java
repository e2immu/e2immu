package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;

public class Loops_5 {

    // same as in 4
    @Constant(absent = true)
    public static int method() {
        int i = 0;
        for (; i < 10; i++) {
            if (i == 1) return 5;
        }
        return 0;
    }


}
