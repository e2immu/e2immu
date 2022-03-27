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

import java.util.Comparator;
import java.util.List;

// as compared to _17... why does this one keep a lambda, and the other one goes into an inlined method?
public class InlinedMethod_17 {

    interface Expression {
    }

    record TypeName(String name) implements Expression, Comparable<TypeName> {
        @Override
        public int compareTo(TypeName o) {
            return name.compareTo(o.name);
        }

        String toUpperCase() {
            return name.toUpperCase();
        }
    }

    record OutputBuilder(List<Expression> expressions) {
        TypeName findTypeName() {
            return (TypeName) expressions.stream().filter(e -> e instanceof TypeName).findFirst().orElseThrow();
        }
    }

    static void method(List<OutputBuilder> perAnnotation) {
        if (perAnnotation.size() > 1) {
            perAnnotation.sort(Comparator.comparing(a -> a.findTypeName().toUpperCase()));
        }
    }
}
