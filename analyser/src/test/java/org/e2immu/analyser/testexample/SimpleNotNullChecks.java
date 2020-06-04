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
}
