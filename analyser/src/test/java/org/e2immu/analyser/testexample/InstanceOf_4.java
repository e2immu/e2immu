package org.e2immu.analyser.testexample;

public class InstanceOf_4 {

    /*
    when it occurs in a negation it goes beyond the if statement
     */
    public static String method(Object in) {
        if (!(in instanceof Number number)) {
            return "Not a number";
        }
        return "Number: " + number;
    }
}
