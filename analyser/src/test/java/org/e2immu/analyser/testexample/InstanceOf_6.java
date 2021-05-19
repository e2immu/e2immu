package org.e2immu.analyser.testexample;

public class InstanceOf_6 {

    public static String method(Object in) {
        if (in instanceof Number && in == null) { // must raise error
            throw new UnsupportedOperationException();
        }
        return ""+in;
    }
}
