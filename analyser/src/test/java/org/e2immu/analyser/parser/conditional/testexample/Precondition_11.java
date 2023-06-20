package org.e2immu.analyser.parser.conditional.testexample;

public abstract class Precondition_11 {

    abstract int compute(int i);

    public int method(boolean a, boolean b, int i) {
        if (!a) {
            int c = compute(i);
            if (!b) {
                return 2 * c;
            }
            if(b) {
                return c + 3;
            }
        } else {
            return 12;
        }
        throw new UnsupportedOperationException("Cannot reach this");
    }
}
