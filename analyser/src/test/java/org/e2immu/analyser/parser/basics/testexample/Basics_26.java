package org.e2immu.analyser.parser.basics.testexample;

public class Basics_26 {

    // binary operator
    public static String method1(String in) {
        String tmp;
        return in == null ? null : "Not null: " + (tmp = in.toUpperCase()) + " == " + tmp;
    }

    // also binary operator
    public static String method2(String in) {
        String tmp;
        return in == null ? null : "Not null: " + (tmp = in).toUpperCase() + " == " + tmp;
    }

    // method object to method parameter
    public static boolean method3(String in) {
        String tmp;
        return (tmp = in).contains(tmp.toUpperCase());
    }

    // one parameter to the next
    public static String method4(String in) {
        String tmp;
        return in.substring((tmp = in).length()-5, tmp.length());
    }
}
