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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NullNotAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

public class NullParameterChecks {
    private static final Logger LOGGER = LoggerFactory.getLogger(NullParameterChecks.class);

    private String s;

    public void method1(@NullNotAllowed String s) {
        if (s == null) throw new NullPointerException();
        this.s = s;
    }

    public void method2(@NullNotAllowed String s) {
        this.s = Objects.requireNonNull(s);
    }

    public void method3(@NullNotAllowed String s) {
        this.s = s;
        if (s == null) throw new NullPointerException();
    }

    public void method4(@NullNotAllowed String s) {
        Objects.requireNonNull(s);
        this.s = s;
    }

    public void method5(@NullNotAllowed String s) {
        if (s == null) {
            LOGGER.error("Have null parameter");
            throw new NullPointerException();
        }
        this.s = s;
    }

    public void method6(@NullNotAllowed String s) {
        if (s == null) {
            LOGGER.debug("Have null parameter");
            throw new NullPointerException();
        }
        this.s = s;
    }

    public void method7Implicit(@NullNotAllowed String s) {
        this.s = s.strip();
    }

    public void method8Implicit(@NullNotAllowed String s) {
        if (s != null) {
            this.s = s.strip();
        } else {
            this.s = "abc";
        }
    }

    public int method9(@NullNotAllowed String k) {
        return s == null ? k.length() : s.length();
    }

    public int method10(@NullNotAllowed String t, @NullNotAllowed String k) {
        return t == null ? k.length() : t.length();
    }

    // we have no way of knowing if the lambda will be executed... but we cannot have a "bomb" waiting
    public String method11Lambda(@NullNotAllowed String t) {
        Supplier<String> supplier = () -> t.trim()+".";
        return supplier.get();
    }


    // we have no way of knowing if the lambda will be executed... but we cannot have a "bomb" waiting
    public String method12LambdaBlock(@NullNotAllowed String t) {
        Supplier<String> supplier = () -> {
            return t.trim()+".";
        };
        return supplier.get();
    }
}
