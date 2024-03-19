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

import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.TypeName;

/*
provides sufficient information to determine whether a variable or type name has to qualified
in the current context.

E.g., even in minimal output mode, a field 'i' will need to be referred to as 'this.i' when there
is a local variable or parameter named 'i'.
 */
public interface Qualification {

    /*
    used for logging
     */
    Qualification EMPTY = typeInfo -> TypeName.Required.SIMPLE;

    /*
    used to generate names of parameterized types
     */
    Qualification DISTINGUISHING_NAME = new Qualification() {

        @Override
        public TypeName.Required qualifierRequired(TypeInfo typeInfo) {
            return TypeName.Required.FQN;
        }

        @Override
        public boolean useNumericTypeParameters() {
            return true;
        }
    };

    /*
    used to generate names of parameterized types
    */
    Qualification FULLY_QUALIFIED_NAME = typeInfo -> TypeName.Required.FQN;
    /*
    used for translation to OpenRewrite, com.foo.Bar$Bar2 instead of com.foo.Bar.Bar2
     */
    Qualification DOLLARIZED_FQN = typeInfo -> TypeName.Required.DOLLARIZED_FQN;

    default boolean useNumericTypeParameters() {
        return false;
    }

    /* for FieldReference and This */
    default boolean qualifierRequired(Variable variable) {
        return false;
    }

    default boolean qualifierRequired(MethodInfo methodInfo) {
        return true;
    }

    TypeName.Required qualifierRequired(TypeInfo typeInfo);

    default boolean doNotQualifyImplicit() { return false; }
}
