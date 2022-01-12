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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

public class NullParameterChecks {
    private static final Logger LOGGER = LoggerFactory.getLogger(NullParameterChecks.class);

    private String s;

    public void method1(@NotNull String s) {
        if (s == null) throw new NullPointerException();
        this.s = s;
    }

    public void method2(@NotNull String s) {
        this.s = Objects.requireNonNull(s);
    }

    public void method3(@NotNull String s) {
        this.s = s;
        if (s == null) throw new NullPointerException();
    }

    public void method4(@NotNull String s) {
        Objects.requireNonNull(s);
        this.s = s;
    }

    public void method5(@NotNull String s) {
        if (s == null) {
            LOGGER.error("Have null parameter");
            throw new NullPointerException();
        }
        this.s = s;
    }

    public void method6(@NotNull String s) {
        if (s == null) {
            LOGGER.debug("Have null parameter");
            throw new NullPointerException();
        }
        this.s = s;
    }

    public void method7Implicit(@NotNull String s) {
        this.s = s.strip();
    }

    public void method8Implicit(@NotNull(absent = true) String s) {
        if (s != null) {
            this.s = s.strip();
        } else {
            this.s = "abc";
        }
    }

    public int method9(@NotNull String k) {
        return s == null ? k.length() : s.length(); // ERROR: can generate null pointer
    }

    // this should be different from method9, as 's' cannot be a known value, and this.s.length() can
    // generate a null-pointer exception
    public int method9Bis(@NotNull String k) {
        String s2 = this.s;
        return s2 == null ? k.length() : s2.length();
    }

    // is just here to check that we can have a local variable withe the same name as the field
    public int method9Ter(@NotNull String k) {
        String s = this.s;
        return s == null ? k.length() : s.length();
    }

    public static int method10(@NotNull(absent = true) String t, @NotNull String k) {
        return t == null ? k.length() : t.length();
    }

    @NotNull
    public static String method11Lambda(@NotNull String t) {
        Supplier<String> supplier = () -> t.trim() + ".";
        return supplier.get();
    }

    @NotNull
    public static String method12Lambda(@NotNull String t) {
        Supplier<String> supplier = () -> {
            return t.trim() + ".";
        };
        return supplier.get();
    }
}
