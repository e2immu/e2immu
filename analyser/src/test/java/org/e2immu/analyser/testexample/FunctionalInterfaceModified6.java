/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Final;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.function.Function;

public class FunctionalInterfaceModified6 {

    @NotModified
    @Final
    private Function<String, Integer> function1;

    @NotModified
    private Function<String, Integer> function2 = function1;

    @NotModified
    private Function<String, Integer> function3 = String::length;

    private String field1;

    @NotModified
    private Function<String, Integer> function4 = s -> {
        return s.lastIndexOf(s.charAt(0));
    };

    @Modified
    private Function<String, Integer> function5 = s -> {
        field1 = s;
        return s.lastIndexOf(s.charAt(0));
    };

    @Modified
    @Final
    private Function<String, Integer> function6;

    public FunctionalInterfaceModified6(char c) {
        function1 = s -> s.lastIndexOf(c);
        function6 = new Function<String, Integer>() {
            @Override
            public Integer apply(String s) {
                field1 = s;
                return s.indexOf(s.charAt(0), 1);
            }
        };
    }

    @NotModified
    public int index1(String s) {
        return function1.apply(s);
    }

    @NotModified
    public int index2(String s) {
        return function2.apply(s);
    }

    @NotModified
    public int index3(String s) {
        return function3.apply(s);
    }

    @NotModified
    public int index4(String s) {
        return function4.apply(s);
    }

    @Modified
    public int index5(String s) {
        return function5.apply(s);
    }

    @Modified
    public int index6(String s) {
        return function5.apply(s);
    }

    @NotModified
    public String getField1() {
        return field1;
    }
}
