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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.VariableProperty.*;

public interface ParameterAnalysis extends Analysis {

    static ParameterAnalysis createEmpty(ParameterInfo parameterInfo) {
        return () -> new Location(parameterInfo);
    }

    enum AssignedOrLinked {
        ASSIGNED(Set.of(EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD, EXTERNAL_PROPAGATE_MOD, EXTERNAL_IMMUTABLE)),
        LINKED(Set.of(MODIFIED_OUTSIDE_METHOD, EXTERNAL_PROPAGATE_MOD)),
        NO(Set.of()),
        DELAYED(null);

        public static final Set<VariableProperty> PROPERTIES = Set.of(EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD,
                EXTERNAL_PROPAGATE_MOD, EXTERNAL_IMMUTABLE);
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

    // the reason the following methods sit here, is that they are shared by ParameterAnalysisImpl and ParameterAnalysisImpl.Builder

    default int getParameterPropertyCheckOverrides(AnalysisProvider analysisProvider,
                                                   ParameterInfo parameterInfo,
                                                   VariableProperty variableProperty) {
        int mine = getPropertyAsIs(variableProperty);
        int max;
        if (parameterInfo.owner.shallowAnalysis()) {
            int bestOfOverrides = Level.DELAY;
            for (MethodAnalysis override : analysisProvider.getMethodAnalysis(parameterInfo.owner).getOverrides(analysisProvider)) {
                ParameterAnalysis parameterAnalysis = override.getParameterAnalyses().get(parameterInfo.index);
                int overrideAsIs = parameterAnalysis.getPropertyAsIs(variableProperty);
                bestOfOverrides = Math.max(bestOfOverrides, overrideAsIs);
            }
            max = Math.max(mine, bestOfOverrides);
        } else {
            max = mine;
        }
        if (max == Level.DELAY && parameterInfo.owner.shallowAnalysis()) {
            // no information found in the whole hierarchy
            if (variableProperty == MODIFIED_VARIABLE && parameterInfo.owner.isAbstract()) {
                return Level.DELAY;
            }
            return variableProperty.valueWhenAbsent(annotationMode());
        }
        return max;
    }

    default int getParameterProperty(AnalysisProvider analysisProvider,
                                     ParameterInfo parameterInfo,
                                     VariableProperty variableProperty) {
        switch (variableProperty) {
            case IDENTITY:
                return parameterInfo.index == 0 ? Level.TRUE : Level.FALSE;

            case INDEPENDENT_PARAMETER:
                int ip = internalGetProperty(INDEPENDENT_PARAMETER);
                if (ip != Level.DELAY) return ip;
                int cd = getParameterProperty(analysisProvider, parameterInfo, CONTEXT_DEPENDENT);
                int i = getParameterProperty(analysisProvider, parameterInfo, INDEPENDENT);
                if (cd == Level.DELAY || i == Level.DELAY) return Level.DELAY;
                return MultiLevel.bestNotNull(cd, i);

            case PROPAGATE_MODIFICATION:
                int pm = getPropertyAsIs(PROPAGATE_MODIFICATION);
                if (pm != Level.DELAY) return pm; // contracted
                int cpm = getParameterProperty(analysisProvider, parameterInfo, CONTEXT_PROPAGATE_MOD);
                int epm = getParameterProperty(analysisProvider, parameterInfo, EXTERNAL_PROPAGATE_MOD);
                if (cpm == Level.TRUE || epm == Level.TRUE) return Level.TRUE;
                if (cpm == Level.DELAY || epm == Level.DELAY) return Level.DELAY;
                return Level.FALSE;

            case MODIFIED_VARIABLE:
                //if (parameterInfo.parameterizedType.isE2Immutable(analysisProvider) == Boolean.TRUE) {
                //    return Level.FALSE; FIXME shortcut
                //}
                if (!parameterInfo.owner.isPrivate() && analysisProvider.getTypeAnalysis(parameterInfo.owner.typeInfo)
                        .getProperty(VariableProperty.CONTAINER) == Level.TRUE) {
                    return Level.FALSE;
                }
                int mv = getPropertyAsIs(MODIFIED_VARIABLE);
                if (mv != Level.DELAY) return mv;// || parameterInfo.owner.isAbstract()) return mv;
                if (parameterInfo.owner.isAbstract()) {
                    return getParameterPropertyCheckOverrides(analysisProvider, parameterInfo, MODIFIED_VARIABLE);
                }
                int cm = getParameterProperty(analysisProvider, parameterInfo, CONTEXT_MODIFIED);
                int mom = getParameterProperty(analysisProvider, parameterInfo, MODIFIED_OUTSIDE_METHOD);
                if (cm == Level.DELAY || mom == Level.DELAY) return Level.DELAY;
                return Math.max(cm, mom);

            case CONTEXT_MODIFIED:
            case MODIFIED_OUTSIDE_METHOD: {
                // if the parameter is level 2 immutable, it cannot be modified
                // int immutable = getParameterProperty(analysisProvider, parameterInfo, IMMUTABLE);
                // if (immutable >= MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK) {
                //     return Level.FALSE;
                // }FIXME shortcut
                if (!parameterInfo.owner.isPrivate() && analysisProvider.getTypeAnalysis(parameterInfo.owner.typeInfo)
                        .getProperty(VariableProperty.CONTAINER) == Level.TRUE) {
                    return Level.FALSE;
                }
                // now we rely on the computed value
                break;
            }

            // the only way to have a container is for the type to be a container, or for the user to have
            // contract annotated the parameter with @Container
            case CONTAINER: {
                Boolean implicit = parameterInfo.parameterizedType.isImplicitlyImmutable(analysisProvider, parameterInfo.owner.typeInfo);
                if (implicit == Boolean.TRUE) return Level.TRUE;
                // if implicit is null, we cannot return FALSE, we'll have to wait!
                TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
                int withoutImplicitDelay;
                if (bestType != null) {
                    withoutImplicitDelay = analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.CONTAINER);
                } else {
                    withoutImplicitDelay = Level.best(internalGetProperty(VariableProperty.CONTAINER), Level.FALSE);
                }
                return implicit == null && withoutImplicitDelay != Level.TRUE ? Level.DELAY : withoutImplicitDelay;
            }

            case IMMUTABLE: {
                TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
                int formalImmutable;
                if (bestType != null) {
                    TypeAnalysis bestTypeAnalysis = analysisProvider.getTypeAnalysis(bestType);
                    formalImmutable = bestTypeAnalysis.getProperty(VariableProperty.IMMUTABLE);
                } else {
                    formalImmutable = MultiLevel.NOT_INVOLVED;
                }
                int external = getParameterProperty(analysisProvider, parameterInfo, EXTERNAL_IMMUTABLE);
                int context = getParameterProperty(analysisProvider, parameterInfo, CONTEXT_IMMUTABLE);
                if (external == variableProperty.best || context == variableProperty.best) return variableProperty.best;
                if (external == Level.DELAY || context == Level.DELAY) return Level.DELAY;

                return MultiLevel.bestImmutable(formalImmutable, MultiLevel.bestImmutable(external, context));
            }

            case NOT_NULL_EXPRESSION:
                return MultiLevel.NULLABLE;

            case NOT_NULL_PARAMETER:
                int nnp = internalGetProperty(NOT_NULL_PARAMETER);
                if (nnp != Level.DELAY) return nnp;
                int cnn = getParameterProperty(analysisProvider, parameterInfo, CONTEXT_NOT_NULL);
                int enn = getParameterProperty(analysisProvider, parameterInfo, EXTERNAL_NOT_NULL);
                if (cnn == Level.DELAY || enn == Level.DELAY) return Level.DELAY;
                // note that ENN can be MultiLevel.DELAY, but CNN cannot have that value; it must be at least NULLABLE
                return MultiLevel.bestNotNull(cnn, enn);

            case CONTEXT_NOT_NULL: {
                TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
                if (Primitives.isPrimitiveExcludingVoid(bestType)) return MultiLevel.EFFECTIVELY_NOT_NULL;
                break;
            }

            case NOT_MODIFIED_1:
                if (!parameterInfo.parameterizedType.isFunctionalInterface()) {
                    return Level.FALSE;
                }
                break;

            default:
        }
        return internalGetProperty(variableProperty);
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
