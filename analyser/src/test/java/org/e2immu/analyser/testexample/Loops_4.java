package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

public class Loops_4 {

    // important here is that i==0 is not a constant expression, because i is a loop variable
    // the interesting value to check here is 1, because the i++ is evaluated BEFORE the i<10 and the i++
    // at the moment
    public static int method4() {
        for (int i = 0; i < 10; i++) {
            if (i == 1) return 4;
        }
        return 0;
    }

}
