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

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.parser.Primitives;

public class ImplicitProperties {

    public static DV arrayProperties(VariableProperty variableProperty) {
        return switch (variableProperty) {
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV;
            case CONTAINER -> Level.TRUE_DV;
            default -> Level.NOT_INVOLVED_DV;
        };
    }

    public static DV primitiveProperties(VariableProperty variableProperty) {
        return switch (variableProperty) {
            case CONTEXT_MODIFIED, MODIFIED_VARIABLE, MODIFIED_OUTSIDE_METHOD -> Level.FALSE_DV;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;
            case CONTAINER -> Level.TRUE_DV;
            case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
            case NOT_NULL_EXPRESSION, NOT_NULL_PARAMETER -> MultiLevel.EFFECTIVELY_NOT_NULL_DV; // NOT: EXTERNAL_NOT_NULL!
            default -> Level.NOT_INVOLVED_DV;
        };
    }

    public static DV fromType(ParameterizedType parameterizedType, VariableProperty variableProperty) {
        if (parameterizedType.arrays > 0) {
            DV arrayPropertyValue = arrayProperties(variableProperty);
            if (arrayPropertyValue != Level.NOT_INVOLVED_DV) return arrayPropertyValue;
        }
        if (Primitives.isPrimitiveExcludingVoid(parameterizedType)) {
            DV primitivePropertyValue = primitiveProperties(variableProperty);
            if (primitivePropertyValue != Level.NOT_INVOLVED_DV) return primitivePropertyValue;
        }
        return Level.NOT_INVOLVED_DV;
    }
}
