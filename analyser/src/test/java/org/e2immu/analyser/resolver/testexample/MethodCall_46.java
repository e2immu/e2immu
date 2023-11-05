package org.e2immu.analyser.resolver.testexample;

import java.io.Serializable;

/*
Does not yet catch the issue
 */
public class MethodCall_46 {
    interface I {
    }

    interface L extends Serializable, I {
        long getId();
    }

    int method(String s, L l, int k) {
        return (s + l).length() + k;
    }

    static <T extends I> T filterByID(T t, long l) {
        return f(t, new long[]{l}, null);
    }

    static <T extends I> T filterByID(T t, long[] longs) {
        return f(t, longs, null);
    }

    static <T extends I> T f(T s, long[] l, T t) {
        return t;
    }

    int test1(L l) {
        return method("abc", l, 3);
    }

    int test2(L l) {
        return method("abc", filterByID(l, l.getId()),6);
    }

    int test3(L l) {
        return method("abc", filterByID(l, new long[] {l.getId()}),9);
    }
}
