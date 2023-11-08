package org.e2immu.analyser.resolver.testexample;

public class MethodCall_63 {

    interface I {
    }

    void set(I[] is) {
    }

    // from org.apache.commons.lang3
    @SafeVarargs
    static <T> T[] addAll(final T[] array1, final T... array2) {
        return null;
    }

    void method(I i, I data[]) {
        set(addAll(new I[]{i}, data));
    }
}
