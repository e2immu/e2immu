package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public class SimpleNotNullChecks {
    public static final String MESSAGE = "Was null...";

    /*
    method1 cannot be solved with the current state of the analyser,
    which does not really handle conditional blocks.

    method 4 can be solved, because of the single assignment.
    it makes sense to write a pattern that recognizes the situation of method 1, which is not ideal
     */

    @NotNull
    public static String method1(String a1) {
        String s1 = a1;
        if (s1 == null) {
            s1 = MESSAGE;
        }
        return s1;
    }

    @NotNull
    public static String method2(String a2) {
        return a2 == null ? MESSAGE : a2;
    }

    @NotNull
    public static String method3(String a1) {
        if (a1 == null) {
            return MESSAGE;
        }
        return a1;
    }

    @NotNull
    public static String method4(String a1) {
        String s1;
        if (a1 == null) {
            s1 = MESSAGE;
        } else {
            s1 = a1;
        }
        return s1;
    }

    @NotNull
    public static String method5(@Nullable String a1) {
        return Objects.requireNonNullElse(a1, MESSAGE);
    }

    @NotNull
    public static String method6(@Nullable String a1) {
        return conditionalValue(a1, t -> t == null, MESSAGE);
    }

    private static <T> T conditionalValue(T initial, Predicate<T> condition, T alternative) {
        return condition.test(initial) ? alternative : initial;
    }
}
