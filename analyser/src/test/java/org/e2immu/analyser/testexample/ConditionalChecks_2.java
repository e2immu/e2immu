package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

public class ConditionalChecks_2 {

    // this is the one that most people will use, which is technically identical because of the short-circuit in method2's condition
    public static String method3(@NotNull String a, @NotNull String b) {
        if (a == null) {
            throw new NullPointerException();
        }
        if (b == null) {
            throw new NullPointerException();
        }
        return a + b;
    }

}
