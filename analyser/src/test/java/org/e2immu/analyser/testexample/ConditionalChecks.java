package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

public class ConditionalChecks {

    public static int method1(boolean a, boolean b) {
        if (a && b) return 1;
        if (!a && !b) return 2;
        if (a && !b) return 3;
        if (!a && b) return 4; // ERROR: conditional evaluates to constant
        int c = 0; // ERROR: unreachable statement
        return 5;//  unreachable statement, but no error anymore
    }

    // first way of ensuring that both a and b are not null
    public static String method2(@NotNull String a, @NotNull String b) {
        if (a == null || b == null) throw new NullPointerException();
        return a + b;
    }

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

    // this is something different indeed
    public static String method4(@Nullable String a, @Nullable String b) {
        if (a == null && b == null) {
            throw new NullPointerException();
        }
        return a + b;
    }

    private final int i;

    public ConditionalChecks(int i) {
        this.i = i;
    }

    public boolean method5(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConditionalChecks conditionalChecks = (ConditionalChecks) o;
        return i == conditionalChecks.i;
    }
}
