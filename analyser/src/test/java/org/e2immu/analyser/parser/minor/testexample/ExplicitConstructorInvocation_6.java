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

    public ExplicitConstructorInvocation_6(String packageName1, String simpleName1) {
        this(generate(), packageName1, simpleName1);
    }
    // internally, this(...) is replaced by (assignments taken from real constructor)
    // this.identifier = generate();
    // this.packageName = packageName;
    // this.simpleName = simpleName;
    // this.fullyQualifiedName = "".equals(packageName) ? simpleName: packageName+"."+simpleName
    // in each of these 4 expressions, the parameter of the real constructor needs replacing by that of the one with the this(...)

    public ExplicitConstructorInvocation_6(int identifier2, String packageName2, String simpleName2) {
        assert packageName2 != null && !packageName2.isBlank();
        assert simpleName2 != null && !simpleName2.isBlank();
        this.identifier = identifier2;
        this.simpleName = Objects.requireNonNull(simpleName2);
        this.packageName = packageName2;
        if ("".equals(packageName2)) {
            this.fullyQualifiedName = simpleName2;
        } else {
            this.fullyQualifiedName = packageName2 + "." + simpleName2;
        }
    }

    public ExplicitConstructorInvocation_6(ExplicitConstructorInvocation_6 enclosingType3, String simpleName3) {
        this(generate(), enclosingType3, simpleName3);
    }

    public ExplicitConstructorInvocation_6(int identifier4, ExplicitConstructorInvocation_6 enclosingType4, String simpleName4) {
        this.simpleName = Objects.requireNonNull(simpleName4);
        this.packageName = null;
        this.fullyQualifiedName = enclosingType4.fullyQualifiedName + "." + simpleName4;
        this.identifier = identifier4;
    }
}
