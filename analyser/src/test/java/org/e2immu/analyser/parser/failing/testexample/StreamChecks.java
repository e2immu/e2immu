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

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.IntStream;

public class StreamChecks {

    public static void main(String... args) {
        print(10);
        System.out.println(appendRange2(11));
    }

    public static void print(int n) {
        IntStream.range(0, n).forEach(System.out::println);
    }

    public static <T> T find(Collection<T> ts, Predicate<T> predicate) {
        return ts.stream().filter(predicate).findFirst().orElse(null);
    }

    public static String appendRange(int n) {
        StringBuilder sb = new StringBuilder();
        IntStream.range(0, n).peek(System.out::println).forEach(sb::append);
        return sb.toString();
    }

    public static String appendRange2(int n) {
        return IntStream.range(0, n)
                .peek(System.out::println)
                .collect(StringBuilder::new,
                        StringBuilder::append,
                        StringBuilder::append).toString();
    }
}
