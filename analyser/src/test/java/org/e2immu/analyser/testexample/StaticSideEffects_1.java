package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.concurrent.atomic.AtomicInteger;

// no static side effects: inside type; modification and assignment inside constructor
@Container
public class StaticSideEffects_1<K> {
    public final K k;
    public final int count;
    @Modified
    @Variable
    @Nullable
    private static AtomicInteger counter;

    public StaticSideEffects_1(K k) {
        this.k = k;
        if (counter == null) {
            counter = new AtomicInteger();
        }
        count = counter.getAndIncrement();
    }
}
