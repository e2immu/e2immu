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
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

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

    // the reason the following methods sit here, is that they are shared by ParameterAnalysisImpl and ParameterAnalysisImpl.Builder

    default int getParameterPropertyCheckOverrides(AnalysisProvider analysisProvider,
                                                   ParameterInfo parameterInfo,
                                                   VariableProperty variableProperty) {
        boolean shallow = parameterInfo.owner.shallowAnalysis();
        int mine = getPropertyFromMapDelayWhenAbsent(variableProperty);
        if (!shallow) return mine;

        int bestOfOverrides = Level.DELAY;
        for (MethodAnalysis override : analysisProvider.getMethodAnalysis(parameterInfo.owner).getOverrides(analysisProvider)) {
            ParameterAnalysis parameterAnalysis = override.getParameterAnalyses().get(parameterInfo.index);
            int overrideAsIs = parameterAnalysis.getPropertyFromMapDelayWhenAbsent(variableProperty);
            bestOfOverrides = Math.max(bestOfOverrides, overrideAsIs);
        }
        int max = Math.max(mine, bestOfOverrides);

        if (max == Level.DELAY) {
            // no information found in the whole hierarchy
            switch (variableProperty) {
                case MODIFIED_VARIABLE -> {
                    if (parameterInfo.parameterizedType.isFunctionalInterface()) {
                        return Level.DELAY;
                    }
                    // note: parameters of unbound type are not necessarily unmodified (see accept's parameter in Consumer)
                    if (parameterInfo.parameterizedType.isE2Immutable(analysisProvider)) {
                        return Level.FALSE;
                    }
                }
                case INDEPENDENT -> {
                    if (parameterInfo.isOfUnboundParameterType(InspectionProvider.DEFAULT) ||
                            parameterInfo.parameterizedType.isE2Immutable(analysisProvider)) {
                        return MultiLevel.EFFECTIVE;
                    }
                }
            }
            return variableProperty.valueWhenAbsent();

        }
        return max;
    }

    default int getParameterProperty(AnalysisProvider analysisProvider,
                                     ParameterInfo parameterInfo,
                                     VariableProperty variableProperty) {
        int propertyFromType = ImplicitProperties.fromType(parameterInfo.parameterizedType, variableProperty);
        if (propertyFromType > Level.DELAY) return propertyFromType;

        switch (variableProperty) {
            case IDENTITY:
                return parameterInfo.index == 0 ? Level.TRUE : Level.FALSE;

            case INDEPENDENT:
                int ip = getPropertyFromMapDelayWhenAbsent(INDEPENDENT);
                if (ip != Level.DELAY) return ip;
                return getParameterPropertyCheckOverrides(analysisProvider, parameterInfo, INDEPENDENT);

            case MODIFIED_VARIABLE: {
                // if the type properties are contracted, and we've decided on @Container, then the parameter is @NotModified
                // if the method is not abstract and @Container is true, then we must have @NotModified
                // but if the method is abstract, and @Container is to be computed, the default is @Modified
                if (!parameterInfo.owner.isPrivate()) {
                    int ownerTypeContainer = analysisProvider.getTypeAnalysis(parameterInfo.owner.typeInfo)
                            .getProperty(VariableProperty.CONTAINER);
                    if (ownerTypeContainer == Level.TRUE
                            && (!parameterInfo.owner.isAbstract() || parameterInfo.owner.typeInfo.typePropertiesAreContracted())) {
                        return Level.FALSE;
                    }
                }
                int mv = getPropertyFromMapDelayWhenAbsent(MODIFIED_VARIABLE);
                if (mv != Level.DELAY) return mv;

                // unless otherwise contracted, non-private abstract methods have modified parameters
                if (!parameterInfo.owner.isPrivate() && parameterInfo.owner.isAbstract()) {
                    return Level.TRUE;
                }
                // note that parameters of E2Immutable type need not necessarily be @NotModified!
                // there can be a modification of the immutable content (see e.g. Modification_23)

                /* TODO replacement code @Dependent1
                if (parameterInfo.owner.isAbstract()) {
                    int pm = analysisProvider.getMethodAnalysis(parameterInfo.owner).getMethodProperty(analysisProvider,
                            PROPAGATE_MODIFICATION);
                    if (pm == Level.TRUE) {
                        // the abstract method either has @PropagateModification, or is the abstract method
                        // in a functional interface
                        if (Primitives.isPrimitiveExcludingVoid(parameterInfo.parameterizedType)) {
                            return Level.FALSE;
                        }
                        // IMPROVE what about delays?
                        if (parameterInfo.parameterizedType.isE2Immutable(analysisProvider)) {
                            return Level.FALSE;
                        }
                        return Level.TRUE; // no decision for this method
                    }
                    if (pm == Level.DELAY) return Level.DELAY; // no decision yet
                    return getParameterPropertyCheckOverrides(analysisProvider, parameterInfo, MODIFIED_VARIABLE);
                } */
                int cm = getParameterProperty(analysisProvider, parameterInfo, CONTEXT_MODIFIED);
                int mom = getParameterProperty(analysisProvider, parameterInfo, MODIFIED_OUTSIDE_METHOD);
                if (cm == Level.DELAY || mom == Level.DELAY) return Level.DELAY;
                return Math.max(cm, mom);
            }

            case CONTEXT_MODIFIED:
            case MODIFIED_OUTSIDE_METHOD: {
                if (!parameterInfo.owner.isPrivate() && analysisProvider.getTypeAnalysis(parameterInfo.owner.typeInfo)
                        .getProperty(VariableProperty.CONTAINER) == Level.TRUE) {
                    return Level.FALSE;
                }
                // now we rely on the computed value
                break;
            }

            /* the only way to have a container is for the type to be a container, or for the user to have
             contract annotated the parameter with @Container. This latter situation makes most sense
             for abstract types
             */
            case CONTAINER: {
                Boolean transparent = parameterInfo.parameterizedType.isTransparent(analysisProvider, parameterInfo.owner.typeInfo);
                if (transparent == Boolean.TRUE) return Level.TRUE;
                // if implicit is null, we cannot return FALSE, we'll have to wait!
                TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
                int withoutDelay;
                if (bestType != null) {
                    withoutDelay = analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.CONTAINER);
                } else {
                    withoutDelay = Level.best(getPropertyFromMapNeverDelay(VariableProperty.CONTAINER), Level.FALSE);
                }
                return transparent == null && withoutDelay != Level.TRUE ? Level.DELAY : withoutDelay;
            }

            case CONTEXT_IMMUTABLE:
            case EXTERNAL_IMMUTABLE:
            case EXTERNAL_NOT_NULL:
                break;

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
                int nnp = getPropertyFromMapDelayWhenAbsent(NOT_NULL_PARAMETER);
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

            case IGNORE_MODIFICATIONS: {
                TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
                if (bestType != null && bestType.isPrimaryType()
                        && bestType.isAbstract()
                        && "java.util.function".equals(bestType.packageName())) {
                    return Level.TRUE;
                }
                break;
            }

            default:
                throw new PropertyException(Analyser.AnalyserIdentification.PARAMETER, variableProperty);
        }
        return getParameterPropertyCheckOverrides(analysisProvider, parameterInfo, variableProperty);
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
