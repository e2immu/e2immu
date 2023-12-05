package org.e2immu.analyser.parser.conditional.testexample;

public class SwitchStatement_8 {

    public static String method(int i, boolean b, boolean c) {
        String s;
        switch (i) { //                       switchCondition in FWD
            case 2: //                        2==i
                s = "u"; // statement 1.0.00  2==i
                System.out.println("!");  //  2==i
                break; // statement 1.0.02    2==i, state 2!=i
            case 3: //                        3==i
                if (b) { // block 1.0.03
                    s = "x"; //               3==i&&b
                    break;
                } //                          3==i&&!b  is previous fed into next
            case 4:
                if (c) {  // block 1.0.04     4==i || 3==i&&!b
                    s = "z"; //               c&&4==i || 3==i&&!b&&c
                    return s;
                } //                          !c&&4==i || 3==i&&!b&&!c
            case 5:
                s = "v"; // statement 1.0.05  5==i || !c&&4==i || 3==i&&!b&&!c
                break; // statement 1.0.06    5!=i && !(!c&&4==i) && !(3==i&&!b&&!c)
            case 6:
                s = "w"; // statement 1.0.07  6==i
                break; // statement 1.0.08    6!=i
            default:
                s = "y"; // statement 1.0.09  2!=i && 3!=i && 4!=i && 5!=i && 6!=i
                break; // statement 1.0.10    2==i || 3==i || 4==i || 5==i || 6==i
        }
        return s;
    }

}
