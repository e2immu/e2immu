package org.e2immu.analyser.parser.minor.testexample;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class Commutable_0 {

    public static void minimax(int a, int b) {
        int x = min(a, b) * max(b, a);
        int y = max(a, b) * min(b, a);
        assert x == y;
    }
}
