package org.e2immu.analyser.testexample;

import org.e2immu.annotation.E1Container;
import org.e2immu.annotation.Modified;

import java.util.concurrent.atomic.AtomicInteger;

// no static side effects: inside type; modification inside constructor
@E1Container
public class StaticSideEffects_0<K> {
    public final K k;
    public final int count;
    @Modified
    private static final AtomicInteger counter = new AtomicInteger();

    public StaticSideEffects_0(K k) {
        this.k = k;
        count = counter.getAndIncrement();
    }
}
