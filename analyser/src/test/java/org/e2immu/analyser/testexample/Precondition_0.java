package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

public class Precondition_0 {

    public static boolean either$Precondition(String e1, String e2) { return e1 != null || e2 != null; }
    public static String either(String e1, String e2) {
        if (e1 == null && e2 == null) throw new UnsupportedOperationException();
        return e1 + e2;
    }

    @NotNull
    public static String useEither1(@NotNull String in1) {
        return either(in1, null);
    }

    @NotNull
    public static String useEither2(@NotNull String in2) {
        return either(null, in2);
    }


    // here we want to propagate the precondition from MethodValue down to the method,
    // very much like we propagate the single not-null

    public static boolean useEither3$Precondition(String f1, String f2) { return f1 != null || f2 != null; }
    public static String useEither3(@Nullable String f1, @Nullable String f2) {
        return either(f1, f2);
    }

}
