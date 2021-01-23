package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@E2Container
@ExtensionClass(of = String.class)
public class IfStatement_0 {

    @NotNull
    @Identity(absent = true)
    public static String method1(String a) {
        if (a == null) return "b";
        return a;
    }

}

