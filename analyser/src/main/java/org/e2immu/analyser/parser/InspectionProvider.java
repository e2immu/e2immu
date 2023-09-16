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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.model.*;
import org.e2immu.annotation.NotNull;

public interface InspectionProvider {

    InspectionProvider DEFAULT = new InspectionProvider() {
        @Override
        public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
            return fieldInfo.fieldInspection.get();
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
            return typeInfo.typeInspection.get("Inspection of type " + typeInfo.fullyQualifiedName);
        }

        @Override
        public MethodInspection getMethodInspection(MethodInfo methodInfo) {
            return methodInfo.methodInspection.get();
        }

        @Override
        public Primitives getPrimitives() {
            throw new UnsupportedOperationException();
        }
    };

    // RESTRICTED TO TESTING ONLY!
    static InspectionProvider defaultFrom(Primitives primitives) {
        return new InspectionProvider() {
            @Override
            public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
                return fieldInfo.fieldInspection.get();
            }

            @Override
            public TypeInspection getTypeInspection(TypeInfo typeInfo) {
                return typeInfo.typeInspection.get();
            }

            @Override
            public MethodInspection getMethodInspection(MethodInfo methodInfo) {
                return methodInfo.methodInspection.get();
            }

            @Override
            public Primitives getPrimitives() {
                return primitives;
            }
        };
    }

    @NotNull
    FieldInspection getFieldInspection(FieldInfo fieldInfo);

    @NotNull
    TypeInspection getTypeInspection(TypeInfo typeInfo);

    @NotNull
    MethodInspection getMethodInspection(MethodInfo methodInfo);

    @NotNull
    Primitives getPrimitives();

    @NotNull
    default MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier, TypeInfo typeInfo, String methodName) {
        throw new UnsupportedOperationException("Not implemented in " + getClass());
    }
}
