package org.e2immu.analyser.testexample;

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.IgnoreModifications;
import org.e2immu.annotation.NotModified;

import java.util.concurrent.atomic.AtomicInteger;

@E2Container
public class StaticSideEffects_4<K> {
    private final K k;

    @IgnoreModifications
    private static final AtomicInteger counter = new AtomicInteger();

    public StaticSideEffects_4(K k) {
        this.k = k;
    }

    @NotModified
    public K getK() {
        counter.getAndIncrement();
        return k;
    }

    public static int countAccessToK() {
        return counter.get();
    }
}
