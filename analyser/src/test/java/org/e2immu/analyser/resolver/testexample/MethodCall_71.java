package org.e2immu.analyser.resolver.testexample;

public class MethodCall_71 {

    static <R> R[] add(R[] rs, R r) {
        return rs;
    }

    static <T> T any1() {
        return null;
    }

    static <S> S any2() {
        return null;
    }

    void method1() {
        add(any1(), any2());
    }

    void method2() {
        add(any1(), any1());
    }
}
