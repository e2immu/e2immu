package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class TryStatement_4 {

    @NotNull(absent = true)
    @Constant(absent = true)
    public static String method(String s) {
        String res;
        try {
            res = "Hi" + Integer.parseInt(s);
        } catch (NullPointerException | NumberFormatException npe) {
            res = null;
        }
        return res;
    }
}