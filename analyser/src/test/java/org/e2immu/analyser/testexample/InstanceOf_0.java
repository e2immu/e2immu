package org.e2immu.analyser.testexample;

public class InstanceOf_0 {

    public static String method(Object in) {
        if (in instanceof Number number) {
            if (number instanceof Integer integer) {
                return "Int: " + integer;
            }
            return "Number: " + number;
        }
        return in.getClass().getCanonicalName();
    }
}
