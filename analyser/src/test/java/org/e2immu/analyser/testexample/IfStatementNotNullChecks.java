package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

public class IfStatementNotNullChecks {

    @NotNull
    public static String method1(String a) {
        if (a == null) return "b";
        return a;
    }

    @NotNull
    public static String method2(String b) {
        if (b == null) return "b";
        else return b;
    }

    @NotNull
    public static String method3(String c) {
        if (c != null) return c;
        return "abc";
    }
}

