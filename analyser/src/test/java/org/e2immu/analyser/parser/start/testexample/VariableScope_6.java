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


import java.util.*;
import java.util.stream.Collectors;

// based on OutputTypeInfo

public class VariableScope_6 {

    record TypeInfo(String packageName) {
    }

    interface Qualification {
        boolean addTypeReturnImport(TypeInfo typeInfo);
    }

    record QualificationImpl() implements Qualification {
        public boolean addTypeReturnImport(TypeInfo typeInfo) {
            return typeInfo.packageName().startsWith("org");
        }
    }

    // FIXME the "= true" assignment marks the difference between _5 and _6. With the assignment, we get a crash, without, a delay loop
    private static class PerPackage {
        final List<TypeInfo> types = new LinkedList<>();
        boolean allowStar = true;
    }

    public static String method(Set<TypeInfo> typesReferenced, String myPackage) {
        Map<String, PerPackage> typesPerPackage = new HashMap<>();
        Qualification qualification = new QualificationImpl();
        typesReferenced.forEach(typeInfo -> {
            String packageName = typeInfo.packageName();
            if (packageName != null && !myPackage.equals(packageName)) {
                boolean doImport = qualification.addTypeReturnImport(typeInfo);
                PerPackage perPackage = typesPerPackage.computeIfAbsent(packageName, p -> new PerPackage());
                if (doImport) {
                    perPackage.types.add(typeInfo);
                } else {
                    perPackage.allowStar = false; // because we don't want to play with complicated ordering
                }
            }
        });
        return typesPerPackage.values().stream()
                .map(pp -> pp.allowStar ? pp.types.get(0).packageName() : "*")
                .collect(Collectors.joining(", "));
    }
}
