package org.e2immu.analyser.resolver.testexample;

import java.io.Serializable;

public class MethodCall_50 {
    public static int size(Serializable o) {
        return o.hashCode();
    }

    public int go(String[] strings) {
        return size(strings);
    }

    public int go2(long[] longs) {
        return size(longs);
    }

    interface X {
    }

    int go3(X[] xs) {
        return size(xs);
    }

    int go4(X x) {
        //  return size(x); ILLEGAL
        return 0;
    }
}
