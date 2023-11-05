package org.e2immu.analyser.resolver.testexample;

// String[][] --> Object[]
public class MethodCall_44 {

    interface Y {
        String method( Object[] objects);
    }

    String test(Y y, String[][] strings) {
        return y.method( strings);
    }
}
