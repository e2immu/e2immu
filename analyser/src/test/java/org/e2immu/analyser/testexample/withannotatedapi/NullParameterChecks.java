/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.testexample.withannotatedapi;

import org.e2immu.annotation.AnnotationType;
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

    public void method8Implicit(@NotNull(type = AnnotationType.VERIFY_ABSENT) String s) {
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

    public static int method10(@NotNull(type = AnnotationType.VERIFY_ABSENT) String t, @NotNull String k) {
        return t == null ? k.length() : t.length();
    }

    // we have no way of knowing if the lambda will be executed... but we cannot have a "bomb" waiting
    public static String method11Lambda(@NotNull String t) {
        Supplier<String> supplier = () -> t.trim() + ".";
        return supplier.get();
    }


    // we have no way of knowing if the lambda will be executed... but we cannot have a "bomb" waiting
    public static String method12LambdaBlock(@NotNull String t) {
        Supplier<String> supplier = () -> {
            return t.trim() + ".";
        };
        return supplier.get();
    }
}
