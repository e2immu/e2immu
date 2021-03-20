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

public enum FieldModifier {

    PRIVATE(0), PUBLIC(0), PROTECTED(0),

    // this one obviously does not exist as a field modifier in Java code, but is useful so we can use this enum as an 'access' type
    PACKAGE(0),

    STATIC(1),

    FINAL(2),
    VOLATILE(2),

    TRANSIENT(3),
    ;

    private final int group;

    FieldModifier(int group) {
        this.group = group;
    }

    private static final int GROUPS = 4;

    public static FieldModifier from(Modifier m) {
        return FieldModifier.valueOf(m.getKeyword().toString().toUpperCase());
    }

    public String toJava() {
        return name().toLowerCase();
    }


    public static String[] sort(Set<FieldModifier> modifiers) {
        FieldModifier[] array = new FieldModifier[GROUPS];
        for (FieldModifier methodModifier : modifiers) {
            if (array[methodModifier.group] != null)
                throw new UnsupportedOperationException("? already have " + array[methodModifier.group]);
            array[methodModifier.group] = methodModifier;
        }
        return Arrays.stream(array).filter(m -> m != null && m != PACKAGE).map(FieldModifier::toJava).toArray(String[]::new);
    }
}
