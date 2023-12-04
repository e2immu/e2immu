package org.e2immu.analyser.resolver.testexample;

public class OldSwitchStatement_0 {

    public static String method(int dataType) {
        String s;
        switch (dataType) {

            case 3: {
                s = "x";
                break;
            }

            case 4:
                s = "z";
                break;

            default:
                s = "y";

        }
        return s;
    }

}
