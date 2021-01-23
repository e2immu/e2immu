package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@E2Container
@ExtensionClass(of = String.class)
public class IfStatement_1 {

    @NotNull
    public static String method2(String b) {
        if (b == null) return "b";
        else return b;
    }

}

