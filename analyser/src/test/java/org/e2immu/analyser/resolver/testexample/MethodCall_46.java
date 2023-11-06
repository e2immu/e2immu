package org.e2immu.analyser.resolver.testexample;

import java.io.Serializable;

public class MethodCall_46 {
    interface I {
        int method1();

        int method2();
    }

    interface L extends Serializable, I {
        int method4();

        int method5();

        int getId();
    }

    int method(L l, String s, int k) {
        return (s + l).length() + k;
    }

    static <T extends I> T filterByID(T t, long l) {
        return f(t, new long[]{l}, null);
    }

    static <T extends I> T f(T s, long[] l, T t) {
        return t;
    }

    static <T extends I> T filterByID(T t, long[] longs) {
        return f(t, longs, null);
    }

    int test1(L l) {
        return method(l, "abc", 3);
    }

    int test2(L l) {
        return method(filterByID(l, 9), "abc", 6);
    }

    int test3(L l) {
        return method(filterByID(l, new long[]{l.getId()}), "abc", 9);
    }
}
