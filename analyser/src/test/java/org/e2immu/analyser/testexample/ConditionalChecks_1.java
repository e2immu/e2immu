package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

public class ConditionalChecks_1 {

    // first way of ensuring that both a and b are not null
    public static String method2(@NotNull String a, @NotNull String b) {
        if (a == null || b == null) throw new NullPointerException();
        return a + b;
    }

}
