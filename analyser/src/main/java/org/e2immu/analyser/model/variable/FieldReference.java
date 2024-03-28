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

package org.e2immu.analyser.model.variable;

import org.e2immu.analyser.model.*;

public interface FieldReference extends Variable {

    FieldInfo fieldInfo();

    Variable scopeVariable();

    Expression scope();

    // this.x
    boolean scopeIsThis();

    // this.x.y as well!
    boolean scopeIsRecursivelyThis();

    Variable thisInScope();

    /*
    the type in which we're evaluating the field reference
    does is the scope mine?
     */
    boolean scopeIsThis(TypeInfo currentType);

    boolean hasAsScopeVariable(Variable pv);

    boolean isDefaultScope();

    default boolean someScopeIsParameterOf(MethodInfo methodInfo) {
        Variable sv = scopeVariable();
        if (sv instanceof ParameterInfo pi && methodInfo.equals(pi.getMethodInfo())) return true;
        if (sv instanceof FieldReference fr) return fr.someScopeIsParameterOf(methodInfo);
        return false;
    }
}
