package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Identity;

public class Warnings_11 {
    public static int method(int i) {
        int j = i;
        while (j < 20) {
            j = idem(j); // assignment to current value
            j--;
            j = j; // assigning to itself
        }
        return j;
    }

    @Identity
    private static int idem(int i) {
        return i;
    }
}
