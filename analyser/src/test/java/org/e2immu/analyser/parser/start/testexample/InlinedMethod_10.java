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

package org.e2immu.analyser.parser.start.testexample;

import java.util.Arrays;
import java.util.stream.Stream;

public record InlinedMethod_10(String[] input) {

    private static String find2(Stream<String> stream, String s, int mode) {
        return mode < 1 ? find(stream, s) : stream.filter(ff -> ff.equals(s)).findAny().orElse(s);
    }

    private static String find(Stream<String> stream, String s) {
        return s.length() < 2 ? find2(stream, s, s.length()) : stream.filter(f -> f.contains(s)).findFirst().orElse(null);
    }

    public String method(String t) {
        return find2(Stream.of(t), t, t.length()).startsWith("b") ? find2(Arrays.stream(input), t, 0) : find(Arrays.stream(input), t);
    }

    public String method2(Stream<String> stream) {
        return method(stream.filter(u -> u.startsWith("a")).findFirst().orElse("b"));
    }
}
