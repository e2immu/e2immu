package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotModified;

/*
trivial situations
 */
public class ReturnValue_0 {

    @NotModified
    private static int square(int i) {
        return i * i;
    }

    @NotModified
    public static int cube(int i) {
        return square(i) * i;
    }

    @Constant("27")
    public static int cube3() {
        return cube(3);
    }
}
