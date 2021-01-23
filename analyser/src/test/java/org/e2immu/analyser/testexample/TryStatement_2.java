package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotNull;

public class TryStatement_2 {

    @NotNull
    @Constant("Hi")
    public static String method(String s) {
        String res;
        try {
            res = "Hi";
        } catch (NullPointerException npe) {
            // ERROR 1: assignment is not used
            res = "Null";
            throw npe;
        } catch (NumberFormatException nfe) {
            res = "Not a number";
            throw nfe;
        }
        return res;
    }

}
