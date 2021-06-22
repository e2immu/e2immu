package org.e2immu.analyser.testexample;

public class ConditionalInitialization_4 {
    private static int i;

    public ConditionalInitialization_4(boolean b) {
        if (b) {
            i = i + 1;
        }
    }

    public static int getI() {
        return i;
    }
}
