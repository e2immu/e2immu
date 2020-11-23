package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@E2Container
@ExtensionClass(of = String.class)
public class IfStatementChecks {

    @NotNull
    @Identity(absent = true)
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
    @Constant(absent = true)
    public static String method3(String c) {
        if (c != null) return c;
        return "abc";
    }

    @Constant(absent = true)
    @NotNull
    public static String method4(String c) {
        String res;
        if (c == null) {
            res = "abc";
        } else {
            res = "cef";
        }
        return res;
    }
}

