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
import java.util.Set;

import static org.e2immu.analyser.analyser.VariableProperty.*;

public interface ParameterAnalysis extends Analysis {

    enum AssignedOrLinked {
        ASSIGNED(Set.of(EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD, EXTERNAL_IMMUTABLE)),
        LINKED(Set.of(MODIFIED_OUTSIDE_METHOD)),
        NO(Set.of()),
        DELAYED(null);

        public static final Set<VariableProperty> PROPERTIES = Set.of(EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD,
                EXTERNAL_IMMUTABLE);
        private final Set<VariableProperty> propertiesToCopy;

        AssignedOrLinked(Set<VariableProperty> propertiesToCopy) {
            this.propertiesToCopy = propertiesToCopy;
        }

        public boolean isAssignedOrLinked() {
            return this == ASSIGNED || this == LINKED;
        }

        public Set<VariableProperty> propertiesToCopy() {
            return propertiesToCopy;
        }
    }

    /**
     * The map is valid when isAssignedToFieldDelaysResolved() is true.
     *
     * @return If a parameter is assigned to a field, a map containing at least the entry (fieldInfo, ASSIGNED) is returned.
     * This is the case of an effectively final field.
     * If a parameter is linked to one or more fields (implying the parameter is variable), the map contains pairs (fieldInfo, LINKED).
     * At any time, the map can contain (fieldInfo, NO) tuples.
     */
    default Map<FieldInfo, AssignedOrLinked> getAssignedToField() {
        return null;
    }

    default boolean isAssignedToFieldDelaysResolved() {
        return true;
    }

    default int getParameterProperty(AnalysisProvider analysisProvider,
                                     ParameterInfo parameterInfo,
                                     VariableProperty variableProperty) {


        // some absolutely trivial cases
        int propertyFromType = ImplicitProperties.fromType(parameterInfo.parameterizedType, variableProperty);
        if (propertyFromType > Level.DELAY) return propertyFromType;

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
                return parameterInfo.index == 0 ? Level.TRUE : Level.FALSE;

            case NOT_NULL_EXPRESSION:
                return MultiLevel.NULLABLE;

            case MODIFIED_VARIABLE: {
                int mv = getPropertyFromMapDelayWhenAbsent(MODIFIED_VARIABLE);
                if (mv != Level.DELAY) return mv;
                int cm = getPropertyFromMapDelayWhenAbsent(CONTEXT_MODIFIED);
                int mom = getPropertyFromMapDelayWhenAbsent(MODIFIED_OUTSIDE_METHOD);
                if (cm == Level.DELAY || mom == Level.DELAY) return Level.DELAY;
                return Math.max(cm, mom);
            }

            case IMMUTABLE: {
                int imm = getPropertyFromMapDelayWhenAbsent(IMMUTABLE);
                if (imm != Level.DELAY) return imm;
                int external = getPropertyFromMapDelayWhenAbsent(EXTERNAL_IMMUTABLE);
                int context = getPropertyFromMapDelayWhenAbsent(CONTEXT_IMMUTABLE);
                if (external == variableProperty.best || context == variableProperty.best) return variableProperty.best;
                int formalImmutable = parameterInfo.parameterizedType.defaultImmutable(analysisProvider, true);
                if (external == Level.DELAY || context == Level.DELAY || formalImmutable == Level.DELAY)
                    return Level.DELAY;
                return MultiLevel.bestImmutable(formalImmutable, MultiLevel.bestImmutable(external, context));
            }

            case NOT_NULL_PARAMETER:
                int nnp = getPropertyFromMapDelayWhenAbsent(NOT_NULL_PARAMETER);
                if (nnp != Level.DELAY) return nnp;
                int cnn = getPropertyFromMapDelayWhenAbsent(CONTEXT_NOT_NULL);
                int enn = getPropertyFromMapDelayWhenAbsent(EXTERNAL_NOT_NULL);
                if (cnn == Level.DELAY || enn == Level.DELAY) return Level.DELAY;
                // note that ENN can be MultiLevel.DELAY, but CNN cannot have that value; it must be at least NULLABLE
                return MultiLevel.bestNotNull(cnn, enn);

            default:
                throw new PropertyException(Analyser.AnalyserIdentification.PARAMETER, variableProperty);
        }
        return getPropertyFromMapDelayWhenAbsent(variableProperty);
    }


    default int getPropertyVerifyContracted(VariableProperty variableProperty) {
        int v = getProperty(variableProperty);
        // special code to catch contracted values
        if (variableProperty == NOT_NULL_EXPRESSION) {
            return MultiLevel.bestNotNull(v, getProperty(NOT_NULL_PARAMETER));
        }
        if (variableProperty == MODIFIED_OUTSIDE_METHOD) {
            return Math.max(v, getProperty(MODIFIED_VARIABLE));
        }
        return v;
    }

    default boolean assignedToFieldIsFrozen() {
        return false;
    }
}
