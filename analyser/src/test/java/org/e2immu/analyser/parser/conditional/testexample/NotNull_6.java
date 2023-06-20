package org.e2immu.analyser.parser.conditional.testexample;

public class NotNull_6 {

    private static String compute(String in) {
        if (in == null) return null;
        return in.toUpperCase();
    }

    public static boolean method(String s) {
        return compute(s) == null;
    }

    public static String method2(String in) {
        if (compute(in) == null) return null;
        return "Not null: " + compute(in) + " == " + compute(in);
    }

    public static String method3(String in) {
        String tmp = compute(in);
        if (tmp == null) return null;
        return "Not null: " + tmp + " == " + tmp;
    }

}
