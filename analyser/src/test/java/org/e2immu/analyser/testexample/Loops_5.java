package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;

public class Loops_5 {

    // same as in 4, but with a different variable
    // now we know that i>=10 at the end of the loop, though

    @Constant(absent = true)
    public static int method() {
        int i = 0;
        for (; i < 10; i++) {
            if (i == 1) return 5;
        }
        assert i >= 10;
        return 0;
    }


}
