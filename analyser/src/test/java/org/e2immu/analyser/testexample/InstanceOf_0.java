package org.e2immu.analyser.testexample;

public class InstanceOf_0 {

    public static String method(Object in) {
        if (in instanceof Number number) {
            return "Number: " + number;
        }
        return ""+in;
    }
}
