package org.e2immu.analyser.testexample;

/*
most interesting aspect of this test is that the return value should not propagate 'number' and 'integer'
into statement 1.

 */
public class InstanceOf_2 {

    public static String method(Object in) {
        if (in instanceof Number number) {
            if (number instanceof Integer integer) {
                return "Integer: " + integer;
            }
            return "Number: " + number;
        }
        return "" + in;
    }
}
