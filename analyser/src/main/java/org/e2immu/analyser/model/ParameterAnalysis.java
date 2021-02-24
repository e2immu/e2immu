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

import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.e2immu.analyser.analyser.VariableProperty.*;

public interface ParameterAnalysis extends Analysis {

    static ParameterAnalysis createEmpty(ParameterInfo parameterInfo) {
        return () -> new Location(parameterInfo);
    }

    /**
     * @return Null means: object not yet set (only in the building phase)
     */
    default ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW;
    }


    enum AssignedOrLinked {
        ASSIGNED(Set.of(EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD)),
        LINKED(Set.of(MODIFIED_OUTSIDE_METHOD)),
        NO(Set.of()),
        DELAYED(null);

        public static final Set<VariableProperty> PROPERTIES = Set.of(EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD);
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
        IntStream mine = IntStream.of(getPropertyAsIs(variableProperty));
        IntStream theStream;
        if (isBeingAnalysed()) {
            theStream = mine;
        } else {
            IntStream overrideValues = analysisProvider.getMethodAnalysis(parameterInfo.owner).getOverrides(analysisProvider)
                    .stream()
                    .map(methodAnalysis -> methodAnalysis.getParameterAnalyses().get(parameterInfo.index))
                    .mapToInt(parameterAnalysis -> parameterAnalysis.getPropertyAsIs(variableProperty));
            theStream = IntStream.concat(mine, overrideValues);
        }
        int max = theStream.max().orElse(Level.DELAY);
        if (max == Level.DELAY && !isBeingAnalysed()) {
            // no information found in the whole hierarchy
            return variableProperty.valueWhenAbsent(annotationMode());
        }
        return max;
    }

    default int getParameterProperty(AnalysisProvider analysisProvider,
                                     ParameterInfo parameterInfo,
                                     ObjectFlow objectFlow,
                                     VariableProperty variableProperty) {
        switch (variableProperty) {
            case IDENTITY:
                return parameterInfo.index == 0 ? Level.TRUE : Level.FALSE;

            case MODIFIED_VARIABLE:
                int mv = getPropertyAsIs(MODIFIED_VARIABLE);
                if (mv != Level.DELAY) return mv;
                int cm = getParameterProperty(analysisProvider, parameterInfo, objectFlow, CONTEXT_MODIFIED);
                int mom = getParameterProperty(analysisProvider, parameterInfo, objectFlow, MODIFIED_OUTSIDE_METHOD);
                if (cm == Level.DELAY || mom == Level.DELAY) return Level.DELAY;
                return Math.max(cm, mom);

            case CONTEXT_MODIFIED:
            case MODIFIED_OUTSIDE_METHOD: {
                // if the parameter is level 2 immutable, it cannot be modified
                if (parameterInfo.parameterizedType.isAtLeastEventuallyE2Immutable(analysisProvider) == Boolean.TRUE) {
                    return Level.FALSE;
                }
                if (parameterInfo.parameterizedType.isFunctionalInterface()) {
                    return Level.FALSE; // by definition, see manual
                }
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
                TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
                if (bestType != null)
                    return analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.CONTAINER);
                return Level.best(internalGetProperty(VariableProperty.CONTAINER), Level.FALSE);
            }
            // when can a parameter be immutable? it cannot be computed in the method
            // (1) if the type is effectively immutable
            // (2) if all the flows leading up to this parameter are flows where the mark has been set; but this
            // we can only know when the method is private and the flows are known
            // (3) when the user has contract-annotated the parameter with @E2Immutable
            case IMMUTABLE: {
                TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
                int immutableFromType;
                if (bestType != null) {
                    TypeAnalysis bestTypeAnalysis = analysisProvider.getTypeAnalysis(bestType);
                    int immutable = bestTypeAnalysis.getProperty(VariableProperty.IMMUTABLE);
                    if(immutable == Level.DELAY) return internalGetProperty(VariableProperty.IMMUTABLE);
                    boolean objectFlowCondition = parameterInfo.owner.isPrivate() &&
                            objectFlow != null && objectFlow.getPrevious().allMatch(of -> of.conditionsMetForEventual(bestTypeAnalysis));
                    immutableFromType = MultiLevel.eventual(immutable, objectFlowCondition);
                } else {
                    immutableFromType = MultiLevel.MUTABLE;
                }
                return Math.max(immutableFromType, MultiLevel.delayToFalse(internalGetProperty(VariableProperty.IMMUTABLE)));
            }

            case NOT_NULL_EXPRESSION:
                return MultiLevel.NULLABLE;

            case NOT_NULL_PARAMETER:
                int nnp = internalGetProperty(NOT_NULL_PARAMETER);
                if (nnp != Level.DELAY) return nnp;
                int cnn = getParameterProperty(analysisProvider, parameterInfo, objectFlow, CONTEXT_NOT_NULL);
                int enn = getParameterProperty(analysisProvider, parameterInfo, objectFlow, EXTERNAL_NOT_NULL);
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
            return MultiLevel.bestNotNull(v, getProperty(MODIFIED_VARIABLE));
        }
        return v;
    }

}
