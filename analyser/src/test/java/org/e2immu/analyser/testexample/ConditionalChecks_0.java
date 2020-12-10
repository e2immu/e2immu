package org.e2immu.analyser.testexample;

public class ConditionalChecks_0 {

    public static int method1(boolean a, boolean b) {
        if (a && b) return 1;
        if (!a && !b) return 2;
        if (a && !b) return 3;
        if (!a && b) return 4; // ERROR: conditional evaluates to constant
        int c = 0; // ERROR: unreachable statement
        return 5;//  unreachable statement, but no error anymore
    }

}
