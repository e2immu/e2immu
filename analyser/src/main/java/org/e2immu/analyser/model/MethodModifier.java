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

package org.e2immu.analyser.model;

import com.github.javaparser.ast.Modifier;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public enum MethodModifier {
    PUBLIC(0), PRIVATE(0), PROTECTED(0),
    ABSTRACT(1), DEFAULT(1),
    FINAL(2), STATIC(2),
    SYNCHRONIZED(3);

    private final int group;
    private static final int GROUPS = 4;

    MethodModifier(int group) {
        this.group = group;
    }

    public static MethodModifier from(Modifier modifier) {
        return MethodModifier.valueOf(modifier.getKeyword().asString().toUpperCase());
    }

    public String toJava() {
        return name().toLowerCase();
    }

    public static String[] sort(Set<MethodModifier> modifiers) {
        MethodModifier[] array = new MethodModifier[GROUPS];
        for (MethodModifier methodModifier : modifiers) {
            if (array[methodModifier.group] != null)
                throw new UnsupportedOperationException("? already have " + array[methodModifier.group]);
            array[methodModifier.group] = methodModifier;
        }
        return Arrays.stream(array).filter(Objects::nonNull).map(MethodModifier::toJava).toArray(String[]::new);
    }
}
