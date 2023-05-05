package org.e2immu.analyser.resolver.testexample;

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.function.BinaryOperator;

public abstract class MethodCall_30 {

    private int j;

    @NotModified
    protected BinaryOperator<Integer> m1;

    @NotModified
    protected abstract int m2(int i, int j);

    @Modified
    public int same1(int k) {
        int d = m2(m1.apply(2, k), m1.apply(j = j + 1, k + 4));
        return Math.max(d, Math.min(k, 10));
    }
}
