package org.e2immu.analyser.resolver.testexample;

import java.io.Serializable;

public class Lambda_17 {
    interface IB {
    }

    interface Filter<T extends IB> {
        boolean accept(T t);
    }

    interface AB extends Serializable {
    }

    static boolean isEmpty(AB par) {
        return par != null;
    }

    interface AA extends Serializable {
    }

    static boolean isEmpty(AA par) {
        return par != null;
    }

    static <T extends AA, B extends IB> T filter(T theSource, T theTarget, Filter<B> filter) {
        return theSource;
    }

    static <T extends AB, B extends IB> T filter(T theSource, T theTarget, Filter<B> filter) {
        return theSource;
    }

    static class R implements Serializable, AB {
    }

    boolean method(R rel) {
        return isEmpty(filter(rel, null, r -> true));
    }
}
