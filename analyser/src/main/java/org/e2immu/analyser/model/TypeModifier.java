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
import java.util.Set;

public enum TypeModifier {
    PUBLIC(0), PROTECTED(0), PRIVATE(0),
    // added to be able to use this type for access privileges
    PACKAGE(0),

    ABSTRACT(1),

    STATIC(2),

    FINAL(3), SEALED(3), NON_SEALED(3);

    TypeModifier(int group) {
        this.group = group;
    }

    private final int group;
    private static final int GROUPS = 4;

    public static TypeModifier from(Modifier modifier) {
        Modifier.Keyword keyword = modifier.getKeyword();
        return TypeModifier.valueOf(keyword.asString().toUpperCase());
    }

    public String toJava() {
        if (this == NON_SEALED) return "non-sealed";
        return name().toLowerCase();
    }

    public static String[] sort(Set<TypeModifier> modifiers) {
        TypeModifier[] array = new TypeModifier[GROUPS];
        for (TypeModifier modifier : modifiers) {
            if (array[modifier.group] != null)
                throw new UnsupportedOperationException("? already have " + array[modifier.group] + ", want to add " + modifier);
            array[modifier.group] = modifier;
        }
        return Arrays.stream(array).filter(m -> m != null && m != PACKAGE).map(TypeModifier::toJava).toArray(String[]::new);
    }
}
