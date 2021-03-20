/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * checks with transformations
 *
 */
public class NotNullWithPatterns {
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
        if (a1 == null) {
            return "abc";
        } else {
            return a1;
        }
    }


    @NotNull
    public static String method4bis(String a1, String a2, String a3) {
        if (a1 == null) {
            if (a2 == null) {
                return "abc";
            } else {
                return a2;
            }
        } else {
            if (a3 == null) {
                return "xyz";
            } else {
                return a1;
            }
        }
    }

    @NotNull
    public static String method5(String a1) {
        String s1;
        if (a1 == null) {
            s1 = MESSAGE;
        } else {
            s1 = a1;
        }
        return s1;
    }

    @NotNull
    public static String method6(@Nullable String a1) {
        return Objects.requireNonNullElse(a1, MESSAGE);
    }

    @NotNull
    public static String method7(@Nullable String a1) {
        return conditionalValue(a1, t -> t == null, MESSAGE);
    }

    @Nullable
    @NotModified
    private static <T> T conditionalValue(@Nullable T initial, Predicate<T> condition, @Nullable T alternative) {
        return condition.test(initial) ? alternative : initial;
    }
}
