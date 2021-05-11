package org.e2immu.analyser.testexample;

public class InstanceOf_5 {

    /*
    when it occurs in a negation it goes beyond the if statement
     */
    public static String method(Object in) {
        String x;
        if (!(in instanceof Number number)) {
            x = "Not a number";
        } else {
            x = "Number: " + number;
        }
        return x; // number NOT present!
    }
}
