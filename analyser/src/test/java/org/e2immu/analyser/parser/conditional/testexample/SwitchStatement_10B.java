package org.e2immu.analyser.parser.conditional.testexample;

public class SwitchStatement_10B {

    public static final int Z = 1;
    public static final int X = 2;
    public static final int Y = 3;
    public static final int U = 4;

    private int v;

    public int method(int cardinality) {
        switch (cardinality) {
            case 9:
            case 13:
                v = Z;
                break;
            case 10:
            case 14:
                v = X;
                break;
            case 15:
                v = Y;
                break;
            default:
                v = U;
        }
        return v;
    }
}
