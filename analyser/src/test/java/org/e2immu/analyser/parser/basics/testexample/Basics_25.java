package org.e2immu.analyser.parser.basics.testexample;

public class Basics_25 {

    public static String method(String s1, String s2) {
        char c = s1.charAt(0);
        int length = s2.length();
        String s = length == 1 ? "!" : length == 0 ? "-" : "~";
        return c + s + c;
    }

}
