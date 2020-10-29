/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.Set;

public interface FieldAnalysis extends Analysis {

    /**
     * @return null means: not decided yet, or field is not effectively final
     */
    Value getEffectivelyFinalValue();

    // end product of the dependency analysis of linkage between the variables in a method
    // if A links to B, and A is modified, then B must be too.
    // In other words, if A->B, then B cannot be @NotModified unless A is too

    // here, the key of the map are fields; the local variables and parameters are stored in method analysis
    // the values are either other fields (in which case these other fields are not linked to parameters)
    // or parameters
    Set<Variable> getVariablesLinkedToMe();

    Boolean getFieldError();

    ObjectFlow getObjectFlow();

    Set<ObjectFlow> getInternalObjectFlows();

    Boolean isOfImplicitlyImmutableDataType();

    default int getFieldProperty(AnalysisProvider analysisProvider,
                                 FieldInfo fieldInfo,
                                 TypeInfo bestType,
                                 VariableProperty variableProperty) {
        switch (variableProperty) {
            case FINAL:
                int immutableOwner = analysisProvider.getTypeAnalysis(fieldInfo.owner).getProperty(VariableProperty.IMMUTABLE);
                if (MultiLevel.isEffectivelyE1Immutable(immutableOwner)) return Level.TRUE;
                break;

            case IMMUTABLE:
                // dynamic type annotation not relevant here
                if (bestType != null && bestType.isFunctionalInterface()) return MultiLevel.FALSE;
                if (fieldInfo.type.arrays > 0) return MultiLevel.MUTABLE;
                int fieldImmutable = internalGetProperty(variableProperty);
                if (fieldImmutable == Level.DELAY) return Level.DELAY;
                int typeImmutable = typeImmutable(analysisProvider, fieldInfo, bestType);
                return MultiLevel.bestImmutable(typeImmutable, fieldImmutable);

            // container is, for fields, a property purely on the type
            case CONTAINER:
                return bestType == null ? Level.TRUE : analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.CONTAINER);

            case NOT_NULL:
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
}
