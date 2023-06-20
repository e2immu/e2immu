package org.e2immu.analyser.parser.conditional.testexample;

public class IfStatement_9 {

    public static String expensiveCall(String in) {
        if (in == null) return null;
        if (in.isEmpty()) return "empty";
        return Character.isAlphabetic(in.charAt(0)) ? in : "non-alpha";
    }

    public static String method(String in) {
        if (expensiveCall(in) == null) return null;
        return "Not null: " + expensiveCall(in);
    }
}
