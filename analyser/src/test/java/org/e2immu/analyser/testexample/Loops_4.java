package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;

public class Loops_4 {

    /*
    Depending on increasing fidelity, the analyser first produces

    - constant 0 (as there is no "state" after the for-loop)
    - then some mix between 0 and 4 (not realising that the if is not conditional given the loop)
    - finally constant 4 (when realising that the inner if will potentially be true)

     */
    @Constant(absent = true)
    public static int method() {
        for (int i = 0; i < 10; i++) {
            if (i == 1) return 4;
        }
        return 0;
    }

}
