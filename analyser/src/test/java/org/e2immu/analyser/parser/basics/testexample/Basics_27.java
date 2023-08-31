package org.e2immu.analyser.parser.basics.testexample;


public class Basics_27 {

    private static String expensiveCall(String in) {
        if (in == null) return null;
        if (in.isEmpty()) return "empty";
        return Character.isAlphabetic(in.charAt(0)) ? in : "non-alpha";
    }

}
