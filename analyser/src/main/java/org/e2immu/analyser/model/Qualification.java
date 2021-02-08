/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    Qualification EMPTY = new Qualification() {
        @Override
        public boolean qualifierRequired(Variable variable) {
            return false;
        }

        @Override
        public boolean qualifierRequired(MethodInfo methodInfo) {
            return true;
        }

        @Override
        public TypeName.Required qualifierRequired(TypeInfo typeInfo) {
            return TypeName.Required.SIMPLE;
        }
    };

    /* for FieldReference and This */
    boolean qualifierRequired(Variable variable);

    boolean qualifierRequired(MethodInfo methodInfo);

    TypeName.Required qualifierRequired(TypeInfo typeInfo);
}
