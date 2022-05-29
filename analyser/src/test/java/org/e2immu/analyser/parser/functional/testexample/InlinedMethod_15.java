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

package org.e2immu.analyser.parser.functional.testexample;

import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

import java.util.LinkedList;
import java.util.List;

public interface InlinedMethod_15 {

    record MethodInfo(String name) {
    }

    @SafeVarargs
    @NotNull1
    static <T> List<T> immutableConcat(@NotNull1 @NotModified Iterable<? extends T>... lists) {
        List<T> builder = new LinkedList<>();
        for (Iterable<? extends T> list : lists) {
            for (T t : list) { // list in not null context, so lists becomes @NotNull1
                builder.add(t); // t in not null context-> list becomes @NotNull1 ... not additive
            }
        }
        return List.copyOf(builder);
    }

    static <T> List<T> concatImmutable(List<T> list1, List<T> list2) {
        if (list1.isEmpty()) return list2;
        if (list2.isEmpty()) return list1;
        return immutableConcat(list1, list2);
    }


    default List<MethodInfo> methodsAndConstructors() {
        return concatImmutable(methods(), constructors());
    }

    List<MethodInfo> methods();

    List<MethodInfo> constructors();
}
