package org.e2immu.analyser.parser.external.testexample;

public class External_10 {

    static int compare(final Number n1, final Number n2) {
        if (method(n1) || method(n2)) {
            return 1;
        }
        return -1;
    }

    static boolean method(final Number n) {
        boolean d = n instanceof Double x && (Double.isNaN(x) || Double.isInfinite(x));
        boolean f = n instanceof Float && (Float.isNaN((Float) n) || Float.isInfinite((Float) n));
        return d || f;
    }
}
