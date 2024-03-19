package org.e2immu.analyser.resolver.testexample;

public class MethodCall_69 {

    public static short eq(int value) {
        return 0;
    }

    public static short eq(char value) {
        return 0;
    }

    public static short eq(short value) {
        return 0;
    }

    public static <T> T eq(T value) {
        return value;
    }

    public static <T> T method() {
        return eq(null);
    }
}
