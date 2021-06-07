package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@Container
public class StaticSideEffects_2<K> {
    private final K k;

    private static int counter;

    public StaticSideEffects_2(K k) {
        this.k = k;
    }

    @Modified
    public K getK() {
        ++counter;
        return k;
    }

    public static int countAccessToK() {
        return counter;
    }
}
