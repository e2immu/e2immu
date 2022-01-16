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

// modelled on a problem with TypeContext

import java.util.Objects;

public class ExplicitConstructorInvocation_5 {

    static class TypeMap {

    }

    private final String packageName;
    private final TypeMap typeMap;
    private final ExplicitConstructorInvocation_5 parent;

    public ExplicitConstructorInvocation_5(TypeMap typeMap) {
        this.typeMap = typeMap;
        parent = null;
        this.packageName = "?";
    }

    public ExplicitConstructorInvocation_5(ExplicitConstructorInvocation_5 parent) {
        this(parent.packageName, parent);
    }

    public ExplicitConstructorInvocation_5(String packageName, ExplicitConstructorInvocation_5 parentContext) {
        this.parent = Objects.requireNonNull(parentContext);
        typeMap = parentContext.typeMap;
        this.packageName = packageName;
    }

}
