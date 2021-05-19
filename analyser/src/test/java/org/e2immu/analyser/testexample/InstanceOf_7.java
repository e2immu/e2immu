package org.e2immu.analyser.testexample;

public class InstanceOf_7 {

    public static String method(Object in, Number n2) {
        if (in instanceof Number number) {
            number = n2; // is a normal variable, can change, and can become null!
            if(number == null) throw new UnsupportedOperationException();
        }
        return "" + in;
    }

}
