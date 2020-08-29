package org.e2immu.analyser.testexample;

public class MatcherChecks {

    public static String method1(String a1) {
        String s1 = a1;
        if (s1 == null) {
            s1 = "";
        }
        return s1;
    }

}
