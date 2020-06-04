package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;

public class InlineMethods {

    private static int product(int x, int y) {
        return x*y;
    }
    private static int square(int x) {
        return product(x, x);
    }

    @Constant(intValue = 6)
    public static final int m1 = product(2, 3);
    @Constant(intValue = 16)
    public static final int m2 = square(4);

}
