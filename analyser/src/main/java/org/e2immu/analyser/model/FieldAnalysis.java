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
import org.e2immu.analyser.parser.Primitives;

public interface FieldAnalysis extends Analysis {

    /**
     * @return effectively final value; null when @Variable
     */
    Expression getEffectivelyFinalValue();

    // end product of the dependency analysis of linkage between the variables in a method
    // if A links to B, and A is modified, then B must be too.
    // In other words, if A->B, then B cannot be @NotModified unless A is too

    // here, the key of the map are fields; the local variables and parameters are stored in method analysis
    // the values are either other fields (in which case these other fields are not linked to parameters)
    // or parameters
    LinkedVariables getLinkedVariables();

    LinkedVariables getLinked1Variables();

    Boolean isTransparentType();

    FieldInfo getFieldInfo();

    ParameterizedType concreteTypeNullWhenDelayed();

    default int getFieldProperty(AnalysisProvider analysisProvider,
                                 FieldInfo fieldInfo,
                                 TypeInfo bestType,
                                 VariableProperty variableProperty) {
        switch (variableProperty) {
            case IMMUTABLE:
                if (fieldInfo.type.arrays > 0) return MultiLevel.MUTABLE;
                int fieldImmutable = getPropertyFromMapDelayWhenAbsent(variableProperty);
                if (fieldImmutable == Level.DELAY && !fieldInfo.owner.shallowAnalysis()) {
                    return Level.DELAY;
                }
                int typeImmutable = fieldInfo.owner == bestType || bestType == null ? MultiLevel.MUTABLE :
                        analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
                return MultiLevel.bestImmutable(typeImmutable, fieldImmutable);

            // container is, for fields, a property purely on the type
            case CONTAINER:
                ParameterizedType concreteType = concreteTypeNullWhenDelayed();
                if (concreteType == null) return Level.DELAY;
                return concreteType.typeInfo == null ? Level.TRUE :
                        analysisProvider.getTypeAnalysis(concreteType.typeInfo).getProperty(VariableProperty.CONTAINER);

            case NOT_NULL_EXPRESSION:
            case CONTEXT_NOT_NULL:
            case NOT_NULL_PARAMETER:
                throw new UnsupportedOperationException("Property " + variableProperty);

            case EXTERNAL_NOT_NULL:
                if (Primitives.isPrimitiveExcludingVoid(fieldInfo.type)) return MultiLevel.EFFECTIVELY_NOT_NULL;
                break;

            default:
        }
        if (fieldInfo.owner.shallowAnalysis()) {
            return getPropertyFromMapNeverDelay(variableProperty);
        }
        return getPropertyFromMapDelayWhenAbsent(variableProperty);
    }

    Expression getInitialValue();
}
