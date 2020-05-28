package org.e2immu.analyser.testexample;

public class ConditionalChecks {

    public static int method1(boolean a, boolean b) {
        if (a && b) return 1;
        if (!a && !b) return 2;
        if (a && !b) return 3;
        if (!a && b) return 4;
        return 5;
    }
}
