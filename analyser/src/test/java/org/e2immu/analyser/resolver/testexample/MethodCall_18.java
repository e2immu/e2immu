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

package org.e2immu.analyser.resolver.testexample;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MethodCall_18 {

    interface AnnotationExpression {
        <T> T extract(String s, T t);
    }

    private final AnnotationExpression ae = new AnnotationExpression() {
        @Override
        public <T> T extract(String s, T t) {
            return s.length() > 0 ? null : t;
        }
    };

    // int
    public String method1() {
        Function<AnnotationExpression, String> f1 = ae -> {
            Integer i = ae.extract("level", 3);
            return i == null ? null : Integer.toString(i);
        };
        return f1.apply(ae);
    }

    // string[]
    public String method2() {
        Function<AnnotationExpression, String> f2 = ae -> {
            String[] inspected = ae.extract("to", new String[]{});
            return Arrays.stream(inspected).sorted().collect(Collectors.joining(","));
        };
        return f2.apply(ae);
    }

    // Integer
    public String method3() {
        Function<AnnotationExpression, String> f3 = ae -> {
            Integer i = ae.extract("level", null);
            return Integer.toString(i);
        };
        return f3.apply(ae);
    }
}
