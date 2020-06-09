package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Precondition;

public class PreconditionChecks {

    @Precondition("(not (null == e1) or not (null == e2))")
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

    @Precondition("not((null == f1) && (null == f2))")
    public static String useEither3(String f1, String f2) {
        return either(f1, f2);
    }
}
