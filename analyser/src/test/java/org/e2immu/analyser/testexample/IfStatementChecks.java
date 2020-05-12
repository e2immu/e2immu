package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@E2Container
@ExtensionClass(of = String.class)
public class IfStatementChecks {

    @NotNull
    @Identity(type = AnnotationType.VERIFY_ABSENT)
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
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static String method3(String c) {
        if (c != null) return c;
        return "abc";
    }

    @Constant(type = AnnotationType.VERIFY_ABSENT)
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

