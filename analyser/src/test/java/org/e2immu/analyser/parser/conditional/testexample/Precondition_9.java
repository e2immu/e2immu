package org.e2immu.analyser.parser.conditional.testexample;

public class Precondition_9 {

    /*
    public static String method(String in) {
        assert in != null && !in.isEmpty();
        char ch = in.charAt(0);
        assert Character.isUpperCase(ch);
        return "(" + ch + ")";
    }

    public String method2(String in) {
        if (in == null) throw new NullPointerException();
        if (in.isEmpty()) throw new UnsupportedOperationException();
        if (Character.isUpperCase(in.charAt(0))) throw new IllegalArgumentException();
        return "(" + in + ")";
    }
*/
    public String method3(String in) {
        if (in == null || in.isEmpty() || Character.isUpperCase(in.charAt(0)))
            throw new UnsupportedOperationException();
        return "(" + in + ")";
    }

}
