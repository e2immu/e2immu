package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.concurrent.atomic.AtomicInteger;

@E1Container
public class StaticSideEffects_3<K> {
    private final K k;

    @Modified
    private static final AtomicInteger counter = new AtomicInteger();

    public StaticSideEffects_3(K k) {
        this.k = k;
    }

    @Modified
    public K getK() {
        counter.getAndIncrement();
        return k;
    }

    @NotModified
    public static int countAccessToK() {
        return counter.get();
    }
}
