package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;

public class InlineMethods_0 {

    private static int product(int x, int y) {
        return x*y;
    }
    private static int square(int x) {
        return product(x, x);
    }

    private static int withIntermediateVariables(int x, int y) {
        int sum = x+y;
        int diff = x-y;
        return sum * diff;
    }

    @Constant("6")
    public static final int m1 = product(2, 3);
    @Constant("16")
    public static final int m2 = square(4);

    @Constant("-24")
    public static final int m3 = withIntermediateVariables(5, 7);
}