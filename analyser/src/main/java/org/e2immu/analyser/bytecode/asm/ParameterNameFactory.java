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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.model.ParameterizedType;

import java.util.HashSet;
import java.util.Set;

public class ParameterNameFactory {
    private final Set<String> names = new HashSet<>();

    public String next(ParameterizedType type) {
        String base;
        if (type.typeInfo != null) {
            // cheap way of finding out with high likelihood if we're dealing with a primitive
            // under no conditions can we trigger a type inspection here!
            if (type.typeInfo.fullyQualifiedName.indexOf('.') < 0) {
                base = firstLetterLowerCase(type.typeInfo.simpleName).substring(0, 1);
            } else {
                base = firstLetterLowerCase(type.typeInfo.simpleName);
            }
        } else if (type.typeParameter != null) {
            base = firstLetterLowerCase(type.typeParameter.getName());
        } else {
            base = "p";
        }
        if (!names.contains(base)) {
            names.add(base);
            return base;
        }
        int index = 1;
        while (true) {
            String name = base + index;
            if (!names.contains(name)) {
                names.add(name);
                return name;
            }
            index++;
        }
    }

    private static String firstLetterLowerCase(String s) {
        return Character.toLowerCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1) : "");
    }
}
