package org.e2immu.analyser.resolver.testexample;

public class OldSwitchStatement_0 {

    public static String method(int dataType) {
        String s;
        a:
        switch (dataType) {

            case 3: {
                s = "x";
                break;
            }

            case 4:
                s = "z";
                b:
                break a;

            default:
                s = "y";

        }
        return s;
    }

}
