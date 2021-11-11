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

import org.e2immu.analyser.analyser.*;

import java.util.Map;

import static org.e2immu.analyser.analyser.VariableProperty.*;

public interface ParameterAnalysis extends Analysis {

    /**
     * The map is valid when isAssignedToFieldDelaysResolved() is true.
     *
     * @return If a parameter is assigned to a field, a map containing at least the entry (fieldInfo, ASSIGNED) is returned.
     * This is the case of an effectively final field.
     * If a parameter is linked to one or more fields (implying the parameter is variable), the map contains pairs (fieldInfo, LINKED).
     * At any time, the map can contain (fieldInfo, NO) tuples.
     */
    default Map<FieldInfo, DV> getAssignedToField() {
        return null;
    }

    default boolean isAssignedToFieldDelaysResolved() {
        return true;
    }

    default DV getParameterProperty(AnalysisProvider analysisProvider,
                                    ParameterInfo parameterInfo,
                                    VariableProperty variableProperty) {


        // some absolutely trivial cases
        DV propertyFromType = ImplicitProperties.fromType(parameterInfo.parameterizedType, variableProperty);
        if (propertyFromType != Level.NOT_INVOLVED_DV) return propertyFromType;

        switch (variableProperty) {
            case CONTAINER:
            case INDEPENDENT:
            case CONTEXT_MODIFIED:
            case MODIFIED_OUTSIDE_METHOD:
            case IMMUTABLE_BEFORE_CONTRACTED:
            case CONTEXT_IMMUTABLE:
            case EXTERNAL_IMMUTABLE:
            case EXTERNAL_NOT_NULL:
            case IGNORE_MODIFICATIONS:
            case CONTEXT_NOT_NULL:
                break;

            case IDENTITY:
                return Level.fromBoolDv(parameterInfo.index == 0);

            case NOT_NULL_EXPRESSION:
                return MultiLevel.NULLABLE_DV;

            case MODIFIED_VARIABLE: {
                DV mv = getPropertyFromMapDelayWhenAbsent(MODIFIED_VARIABLE);
                if (mv.isDone()) return mv;
                DV cm = getPropertyFromMapDelayWhenAbsent(CONTEXT_MODIFIED);
                DV mom = getPropertyFromMapDelayWhenAbsent(MODIFIED_OUTSIDE_METHOD);
                return cm.max(mom);
            }

            case IMMUTABLE: {
                DV imm = getPropertyFromMapDelayWhenAbsent(IMMUTABLE);
                if (imm.isDone()) return imm;
                DV external = getPropertyFromMapDelayWhenAbsent(EXTERNAL_IMMUTABLE);
                if (external.equals(variableProperty.bestDv)) return external;
                DV context = getPropertyFromMapDelayWhenAbsent(CONTEXT_IMMUTABLE);
                if (context.equals(variableProperty.bestDv)) return context;
                DV formalImmutable = parameterInfo.parameterizedType.defaultImmutable(analysisProvider, true);
                return formalImmutable.max(external.max(context));
            }

            case NOT_NULL_PARAMETER: {
                DV nnp = getPropertyFromMapDelayWhenAbsent(NOT_NULL_PARAMETER);
                if (nnp.isDone()) return nnp;
                DV cnn = getPropertyFromMapDelayWhenAbsent(CONTEXT_NOT_NULL);
                DV enn = getPropertyFromMapDelayWhenAbsent(EXTERNAL_NOT_NULL);
                return cnn.max(enn);
            }
            default:
                throw new PropertyException(Analyser.AnalyserIdentification.PARAMETER, variableProperty);
        }
        return getPropertyFromMapDelayWhenAbsent(variableProperty);
    }


    default DV getPropertyVerifyContracted(VariableProperty variableProperty) {
        DV v = getProperty(variableProperty);
        // special code to catch contracted values
        if (variableProperty == NOT_NULL_EXPRESSION) {
            return v.max(getProperty(NOT_NULL_PARAMETER));
        }
        if (variableProperty == MODIFIED_OUTSIDE_METHOD) {
            return v.max(getProperty(MODIFIED_VARIABLE));
        }
        return v;
    }

    default boolean assignedToFieldIsFrozen() {
        return false;
    }
}
