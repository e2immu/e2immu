package org.e2immu.analyser.resolver.testexample;

public class NewSwitchStatement_0 {

    public static String method(int dataType) {
        String s;
        switch (dataType) {

            case 3 -> {
                s = "x";
            }

            case 4 ->
                s = "z";

            default ->
                s = "y";

        }
        return s;
    }

}
