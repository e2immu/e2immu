package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@E2Container
@ExtensionClass(of = String.class)
public class IfStatement_2 {

    @NotNull
    @Constant(absent = true)
    public static String method3(String c) {
        if (c != null) return c;
        return "abc";
    }

}

