package org.e2immu.analyser.testexample;

import java.util.List;

public class MatcherChecks {

    public static String method1(String a1) {
        String s1 = a1;
        if (s1 == null) {
            s1 = "";
        }
        return s1;
    }

    private String s2 = "abc";

    public String method1Negative1(String a1) {
        String s1 = a1;
        if (s1 == null) {
            s2 = "";
            System.out.println(s1);
        }
        return s2;
    }

    public static String method1Negative2(String a1) {
        String s1 = a1;
        if (s1 == null) {
            s1 = "";
        } else {
            s1 = "x";
        }
        return s1;
    }

    public static String method2(String a1) {
        if (a1 == null) {
            return "abc";
        }
        return a1;
    }

    public static String method3(String a1) {
        if ("x".equals(a1)) {
            return "abc";
        } else {
            return a1;
        }
    }

    public static String method4(List<String> strings) {
        for (int i = 0; i < strings.size(); i++) {
            String s = strings.get(i);
            if (s != null && s.length() == 3) return s;
        }
        return "abc";
    }
}
