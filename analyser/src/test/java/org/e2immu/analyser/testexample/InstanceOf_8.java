package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Nullable;

public class InstanceOf_8 {

    public static String method(@Nullable Object in) {
        if (in instanceof String) {
            return in.toString(); // state should say that in != null
        }
        return "Object";
    }

    public static String method2(@Nullable Object in) {
        if (in != null) {
            return in.toString();
        }
        return "Object";
    }
}
