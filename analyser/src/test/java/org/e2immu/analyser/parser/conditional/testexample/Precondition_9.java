package org.e2immu.analyser.parser.conditional.testexample;

public class Precondition_9 {

    public static String method(String in) {
        assert in != null && !in.isEmpty();
        char ch = in.charAt(0);
        assert Character.isUpperCase(ch);
        return "(" + ch + ")";
    }

}
