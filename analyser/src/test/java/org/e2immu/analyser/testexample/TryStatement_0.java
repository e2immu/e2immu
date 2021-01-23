package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class TryStatement_0 {

    /*
    This test has no knowledge of an @NotNull on the parameter of parseInt!
     */

    @NotNull
    @Constant(absent = true)
    public static String method(String s) {
        try {
            return "Hi" + Integer.parseInt(s);
        } catch (NullPointerException npe) {
            return "Null";
        } catch (NumberFormatException nfe) {
            return "Not a number";
        }
    }

}
