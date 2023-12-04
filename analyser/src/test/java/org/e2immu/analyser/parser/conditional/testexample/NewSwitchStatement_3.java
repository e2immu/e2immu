package org.e2immu.analyser.parser.conditional.testexample;

public class NewSwitchStatement_3 {

    public static String method(int dataType) {
        String s;
        switch (dataType) {
            case 3 ->
                s = "x";

            case 4 -> {
                s = "z";
            }
            default ->
                s = "y";

        }
        return s;
    }

}
