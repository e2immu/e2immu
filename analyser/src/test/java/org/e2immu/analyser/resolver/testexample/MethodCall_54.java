package org.e2immu.analyser.resolver.testexample;

public class MethodCall_54 {

    static class Vector {
        Vector(long[] longs) {
        }
    }

    public Object method(long[] longs) {
        return new Vector(longs.clone());
    }

    public long[] method2(long[] longs) {
        return longs.clone();
    }

}
