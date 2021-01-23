package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@E2Container
@ExtensionClass(of = String.class)
public class IfStatement_3 {

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

