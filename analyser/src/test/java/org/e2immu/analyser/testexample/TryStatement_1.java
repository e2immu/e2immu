package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class TryStatement_1 {

    @NotNull
    @Constant(absent = true)
    public static String method(String s) {
        String res;
        try {
            res = "Hi" + Integer.parseInt(s);
        } catch (NullPointerException npe) {
            res = "Null";
        } catch (NumberFormatException nfe) {
            res = "Not a number";
        }
        return res;
    }

}
