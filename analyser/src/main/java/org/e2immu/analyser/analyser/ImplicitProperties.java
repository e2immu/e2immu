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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;

public class ImplicitProperties {

    public static DV arrayProperties(Property property) {
        return switch (property) {
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV;
            case CONTAINER -> MultiLevel.CONTAINER_DV;
            default -> DV.MIN_INT_DV;
        };
    }

    public static DV primitiveProperties(Property property) {
        return switch (property) {
            case CONTEXT_MODIFIED, MODIFIED_VARIABLE, MODIFIED_OUTSIDE_METHOD -> DV.FALSE_DV;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;
            case CONTAINER -> MultiLevel.CONTAINER_DV;
            case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
            case NOT_NULL_EXPRESSION, NOT_NULL_PARAMETER -> MultiLevel.EFFECTIVELY_NOT_NULL_DV; // NOT: EXTERNAL_NOT_NULL!
            default -> DV.MIN_INT_DV;
        };
    }

    public static DV fromType(ParameterizedType parameterizedType, Property property) {
        if (parameterizedType.arrays > 0) {
            DV arrayPropertyValue = arrayProperties(property);
            if (arrayPropertyValue != DV.MIN_INT_DV) return arrayPropertyValue;
        }
        if (parameterizedType.isPrimitiveExcludingVoid()) {
            return primitiveProperties(property);
        }
        return DV.MIN_INT_DV;
    }
}
