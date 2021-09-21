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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.stream.Stream;

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
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case CONTAINER -> Level.TRUE;
            case INDEPENDENT -> MultiLevel.INDEPENDENT;
            case NOT_NULL_EXPRESSION, NOT_NULL_PARAMETER, EXTERNAL_NOT_NULL -> MultiLevel.EFFECTIVELY_NOT_NULL;
            default -> Level.DELAY;
        };
    }

    // INDEPENDENT -> DEP1 or INDEPENDENT
    public static int unboundTypeParameterProperties(VariableProperty variableProperty) {
        return switch (variableProperty) {
            case CONTEXT_MODIFIED, MODIFIED_VARIABLE, MODIFIED_OUTSIDE_METHOD -> Level.FALSE;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case CONTAINER -> Level.TRUE;
            default -> Level.DELAY;
        };
    }

    public static int fromType(ParameterizedType parameterizedType, VariableProperty variableProperty) {
        if (parameterizedType.arrays > 0) {
            int arrayProperty = arrayProperties(variableProperty);
            if (arrayProperty > Level.DELAY) return arrayProperty;
        }
        if (Primitives.isPrimitiveExcludingVoid(parameterizedType)) {
            int primitiveProperty = primitiveProperties(variableProperty);
            if (primitiveProperty > Level.DELAY) return primitiveProperty;
        }
        return Level.DELAY;
    }

    public static int implicitMethodIndependence(MethodInfo methodInfo,
                                                 Stream<ParameterAnalysis> parameterAnalysisStream,
                                                 InspectionProvider inspectionProvider,
                                                 AnalysisProvider analysisProvider) {

        // if all parameters are annotated @Independent or @Dependent1, and the method is a constructor,
        // or, a method returning an independent type, then the method is @Independent

        int minParams = parameterAnalysisStream.mapToInt(parameterAnalysis ->
                parameterAnalysis.getProperty(VariableProperty.INDEPENDENT)).min().orElse(MultiLevel.INDEPENDENT);
        if (minParams <= MultiLevel.DEPENDENT) return minParams; // Level.DELAY, MultiLevel.DEPENDENT

        if (methodInfo.isConstructor || !methodInfo.hasReturnValue()) return MultiLevel.INDEPENDENT;
        return Level.DELAY;//methodInfo.returnTypeIsIndependent(inspectionProvider, analysisProvider);
    }
}
