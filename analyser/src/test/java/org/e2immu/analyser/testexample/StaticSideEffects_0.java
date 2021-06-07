package org.e2immu.analyser.testexample;

import org.e2immu.annotation.E2Container;

import java.util.concurrent.atomic.AtomicInteger;

// no static side effects: inside type; modification inside constructor
@E2Container
public class StaticSideEffects_0<K> {
    public final K k;
    public final int count;
    private static final AtomicInteger counter = new AtomicInteger();

    public StaticSideEffects_0(K k) {
        this.k = k;
        count = counter.getAndIncrement();
    }
}
