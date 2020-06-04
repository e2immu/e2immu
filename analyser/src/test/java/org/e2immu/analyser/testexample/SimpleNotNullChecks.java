package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

public class SimpleNotNullChecks {

    @NotNull
    public static String method1(String a1) {
        String s1 = a1;
        if (s1 == null) {
            s1 = "Was null...";
        }
        return s1;
    }

    @NotNull
    public static String method2(String a2) {
        return a2 == null ? "Was null..." : a2;
    }
}
