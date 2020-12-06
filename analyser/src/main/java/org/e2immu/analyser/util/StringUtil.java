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

package org.e2immu.analyser.util;

import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.UtilityClass;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

@UtilityClass
public class StringUtil {

    private StringUtil() {
        // nothing here, cannot be instantiated
        throw new UnsupportedOperationException();
    }

    public static void indent(StringBuilder sb, int num) {
        sb.append(" ".repeat(Math.max(0, num)));
    }

    @NotNull
    @NotModified
    public static String[] concat(@NotNull String[] s1,
                                  @NotNull String[] s2) {
        String[] res = new String[s1.length + s2.length];
        System.arraycopy(s1, 0, res, 0, s1.length);
        System.arraycopy(s2, 0, res, s1.length, s2.length);
        return res;
    }

    //@NotNull TODO very hard to prove
    @NotModified
    public static <E> String join(@NotNull Collection<E> es, @NotNull Function<E, ?> f) {
        return es.stream().map(f).map(Object::toString).collect(Collectors.joining(", "));
    }

    public static String stripDotClass(String path) {
        if (path.endsWith(".class")) return path.substring(0, path.length() - 6);
        return path;
    }

    public static String quote(String s) {
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }
}
