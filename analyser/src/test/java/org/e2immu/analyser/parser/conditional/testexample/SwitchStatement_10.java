package org.e2immu.analyser.parser.conditional.testexample;

public class SwitchStatement_10 {

    public static final int Z = 1;
    public static final int X = 2;
    public static final int Y = 3;
    public static final int U = 4;

    public static int method(int c) {
        switch (c) {
            case 9:
            case 13:
                return Z;
            case 10:
            case 14:
                System.out.println(c);
                return X;
            case 15:
                return Y;
            default:
                return U;
        }
    }
}
