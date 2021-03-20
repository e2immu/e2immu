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

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.Set;

import static org.e2immu.analyser.analyser.VariableProperty.*;

public interface FieldAnalysis extends Analysis {

    /**
     * @return null means: not decided yet, or field is not effectively final
     */
    Expression getEffectivelyFinalValue();

    // end product of the dependency analysis of linkage between the variables in a method
    // if A links to B, and A is modified, then B must be too.
    // In other words, if A->B, then B cannot be @NotModified unless A is too

    // here, the key of the map are fields; the local variables and parameters are stored in method analysis
    // the values are either other fields (in which case these other fields are not linked to parameters)
    // or parameters
    LinkedVariables getLinkedVariables();

    ObjectFlow getObjectFlow();

    Set<ObjectFlow> getInternalObjectFlows();

    Boolean isOfImplicitlyImmutableDataType();

    default int getFieldProperty(AnalysisProvider analysisProvider,
                                 FieldInfo fieldInfo,
                                 TypeInfo bestType,
                                 VariableProperty variableProperty) {
        switch (variableProperty) {
            case IMMUTABLE:
                // dynamic type annotation not relevant here
                if (bestType != null && bestType.typeInspection.get().isFunctionalInterface()) {
                    return MultiLevel.FALSE;
                }
                if (fieldInfo.type.arrays > 0) return MultiLevel.MUTABLE;
                int fieldImmutable = internalGetProperty(variableProperty);
                if (fieldImmutable == Level.DELAY) return Level.DELAY;
                int typeImmutable = typeImmutable(analysisProvider, fieldInfo, bestType);
                return MultiLevel.bestImmutable(typeImmutable, fieldImmutable);

            // container is, for fields, a property purely on the type
            case CONTAINER:
                return bestType == null ? Level.TRUE : analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.CONTAINER);

            case NOT_NULL_EXPRESSION:
            case CONTEXT_NOT_NULL:
            case NOT_NULL_PARAMETER:
                throw new UnsupportedOperationException("Property "+variableProperty);

            case EXTERNAL_NOT_NULL:
                if (Primitives.isPrimitiveExcludingVoid(fieldInfo.type)) return MultiLevel.EFFECTIVELY_NOT_NULL;
                break;

            default:
        }
        return internalGetProperty(variableProperty);
    }

    private int typeImmutable(AnalysisProvider analysisProvider, FieldInfo fieldInfo, TypeInfo bestType) {
        return fieldInfo.owner == bestType || bestType == null ? MultiLevel.FALSE :
                analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
    }

    Expression getInitialValue();

    default int getPropertyVerifyContracted(VariableProperty variableProperty) {
        int v = getProperty(variableProperty);
        // special code to catch contracted values
        if (variableProperty == NOT_NULL_EXPRESSION) {
            return MultiLevel.bestNotNull(v, getProperty(EXTERNAL_NOT_NULL));
        }
        if (variableProperty == MODIFIED_OUTSIDE_METHOD) {
            return MultiLevel.bestNotNull(v, getProperty(MODIFIED_VARIABLE));
        }
        return v;
    }
}
