package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class SwitchStatement_7 {

    enum Choices { ONE, TWO, THREE, FOUR };

    @NotNull
    @Constant(absent = true)
    public static String method(@NotNull Choices c) {
        String res;
        switch (c) { // forces the @NotNull on c
            case ONE:
                res = "a";
                break;
            case TWO:
                res = "b";
                break;
            default:
                res = "c";
        }
        return res;
    }
}

