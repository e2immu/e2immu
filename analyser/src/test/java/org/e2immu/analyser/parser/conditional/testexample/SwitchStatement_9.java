package org.e2immu.analyser.parser.conditional.testexample;

public class SwitchStatement_9 {

    // test compensating for conditions earlier than switch

    public static String method(int i, boolean b, boolean c) {
        String s;
        if (b) {
            switch (i) { // 1.0.0
                case 3:
                    if (c) { // block 1.0.0.0.0
                        s = "x";
                        break;
                    }
                case 4: {  // block 1.0.0.0.1, abs state 3==i&&b&&!c
                    s = "z";
                    return s;
                }
                case 5, 6:
                    s = "u"; // statement 1.0.0.0.2
                    break;
                default:
                    s = "y"; // statement 1.0.0.0.4
                    break; // statement 1.0.0.0.5
            }
        } else {
            s = "s";
        }
        return s;
    }

}
