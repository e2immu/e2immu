package org.e2immu.analyser.testexample;

import org.e2immu.annotation.E2Container;

import java.util.concurrent.atomic.AtomicInteger;

// no static side effects: inside type; modification and assignment inside constructor
@E2Container
public class StaticSideEffects_1<K> {
    public final K k;
    public final int count;
    private static AtomicInteger counter;

    public StaticSideEffects_1(K k) {
        this.k = k;
        if (counter == null) {
            counter = new AtomicInteger();
        }
        count = counter.getAndIncrement();
    }
}
