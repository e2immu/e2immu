package org.e2immu.analyser.resolver.testexample;

public class MethodCall_36 {
    public static <T> T[] removeObject(T[] arr, T el) {
        return arr;
    }
    public static <T> T[] removeObject(T[] arr, T[] el) {
        return arr;
    }

    public void test() {
        String[] s1 = { "hello" };
        String[] s2 = { "there" };
        removeObject(s1, s2);
    }
}
