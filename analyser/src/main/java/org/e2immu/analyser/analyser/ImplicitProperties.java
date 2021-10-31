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

    public static int arrayProperties(VariableProperty variableProperty) {
        return switch (variableProperty) {
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_E1IMMUTABLE;
            case CONTAINER -> Level.TRUE;
            default -> Level.DELAY;
        };
    }

    public static int primitiveProperties(VariableProperty variableProperty) {
        return switch (variableProperty) {
            case CONTEXT_MODIFIED, MODIFIED_VARIABLE, MODIFIED_OUTSIDE_METHOD -> Level.FALSE;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE;
            case CONTAINER -> Level.TRUE;
            case INDEPENDENT -> MultiLevel.INDEPENDENT;
            case NOT_NULL_EXPRESSION, NOT_NULL_PARAMETER -> MultiLevel.EFFECTIVELY_NOT_NULL; // NOT: EXTERNAL_NOT_NULL!
            default -> Level.DELAY;
        };
    }

    public static int fromType(ParameterizedType parameterizedType, VariableProperty variableProperty) {
        if (parameterizedType.arrays > 0) {
            int arrayPropertyValue = arrayProperties(variableProperty);
            if (arrayPropertyValue > Level.DELAY) return arrayPropertyValue;
        }
        if (Primitives.isPrimitiveExcludingVoid(parameterizedType)) {
            int primitivePropertyValue = primitiveProperties(variableProperty);
            if (primitivePropertyValue > Level.DELAY) return primitivePropertyValue;
        }
        return Level.DELAY;
    }
}
