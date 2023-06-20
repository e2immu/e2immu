package org.e2immu.analyser.parser.start.testexample;

public class Record_2 {

    record Test<X>(X x, String s) {
    }

    public static <X> String method(Test<X> test) {
        if (test.x() == null) {
            return test.s;
        }
        // this should never give a null-pointer exception!
        return test.x().toString();
    }

    public static <X> String method2(Test<X> test) {
        if (test.x == null) {
            return test.s;
        }
        // this should never give a null-pointer exception!
        return test.x.toString();
    }

    public static <X> String method3(Test<X> test) {
        if (test.x() == null) {
            return test.s;
        }
        // this should never give a null-pointer exception!
        return test.x.toString();
    }
}
