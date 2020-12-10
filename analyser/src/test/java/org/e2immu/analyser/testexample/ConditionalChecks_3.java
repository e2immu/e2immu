package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

public class ConditionalChecks_3 {

    // this is something different indeed
    public static String method4(@Nullable String a, @Nullable String b) {
        if (a == null && b == null) {
            throw new NullPointerException();
        }
        return a + b;
    }

}
