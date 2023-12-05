package org.e2immu.analyser.parser.conditional.testexample;

public class SwitchStatement_9 {

    public static String method(int i) {
        String s;
        switch (i) {
            case 3: { // block 1.0.0
                s = "x";
                break;
            }
            case 4: {  // block 1.0.1
                s = "z";
                return s;
            }
            default:
                s = "y"; // statement 1.0.2
                break; // statement 1.0.3
        }
        return s;
    }

}
