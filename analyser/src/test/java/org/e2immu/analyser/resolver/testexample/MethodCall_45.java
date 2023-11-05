package org.e2immu.analyser.resolver.testexample;

import java.io.Serializable;
import java.util.List;

/*
UnboundMethodParameterType in combination with erasure; arrayPenalty
 */
public class MethodCall_45 {

    interface B {
    }

    interface C extends Serializable {
    }

    interface A extends Serializable, B {
    }

    static long[] ids(B[] bs) {
        return new long[]{0L};
    }

    static long[] ids(C c) {
        return new long[]{1L};
    }

    long[] call1(A[] as) {
        return ids(as);
    }

    long[] call2(List<A> as) {
        return ids(as.toArray(new A[0]));
    }
}
