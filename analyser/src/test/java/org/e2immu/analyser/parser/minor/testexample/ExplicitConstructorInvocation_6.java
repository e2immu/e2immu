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

package org.e2immu.analyser.parser.minor.testexample;

import java.util.Objects;

public class ExplicitConstructorInvocation_6 {

    private static int counter;

    private static int generate() {
        return ++counter;
    }

    private final int identifier;
    private final String packageName;
    private final String simpleName;
    private final String fullyQualifiedName;

    public ExplicitConstructorInvocation_6(String packageName, String simpleName) {
        this(generate(), packageName, simpleName);
    }

    public ExplicitConstructorInvocation_6(int identifier, String packageName, String simpleName) {
        assert packageName != null && !packageName.isBlank();
        assert simpleName != null && !simpleName.isBlank();
        this.identifier = identifier;
        this.simpleName = Objects.requireNonNull(simpleName);
        this.packageName = packageName;
        if ("".equals(packageName)) {
            this.fullyQualifiedName = simpleName;
        } else {
            this.fullyQualifiedName = packageName + "." + simpleName;
        }
    }

    public ExplicitConstructorInvocation_6(ExplicitConstructorInvocation_6 enclosingType, String simpleName) {
        this(generate(), enclosingType, simpleName);
    }

    public ExplicitConstructorInvocation_6(int identifier, ExplicitConstructorInvocation_6 enclosingType, String simpleName) {
        this.simpleName = Objects.requireNonNull(simpleName);
        this.packageName = null;
        this.fullyQualifiedName = enclosingType.fullyQualifiedName + "." + simpleName;
        this.identifier = identifier;
    }
}
