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

import org.e2immu.annotation.Final;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.function.Function;

public class FunctionalInterface_6 {

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

    public FunctionalInterface_6(char c) {
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
        return function6.apply(s);
    }

    @NotModified
    public String getField1() {
        return field1;
    }
}
