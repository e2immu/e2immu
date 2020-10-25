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

import java.util.stream.IntStream;

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

    /**
     * @return Null means: not assigned to a field.
     */
    default FieldInfo getAssignedToField() {
        return null;
    }

    default boolean isCopiedFromFieldToParameters() {
        return false;
    }

    // the reason the following methods sit here, is that they are shared by ParameterAnalysisImpl and ParameterAnalysisImpl.Builder

    default int getParameterPropertyCheckOverrides(AnalysisProvider analysisProvider,
                                                   ParameterInfo parameterInfo,
                                                   VariableProperty variableProperty) {
        IntStream mine = IntStream.of(getPropertyAsIs(variableProperty));
        IntStream theStream;
        if (isHasBeenDefined()) {
            theStream = mine;
        } else {
            IntStream overrideValues = analysisProvider.getMethodAnalysis(parameterInfo.owner).getOverrides().stream()
                    .map(methodAnalysis -> methodAnalysis.getParameterAnalyses().get(parameterInfo.index))
                    .mapToInt(parameterAnalysis -> parameterAnalysis.getPropertyAsIs(variableProperty));
            theStream = IntStream.concat(mine, overrideValues);
        }
        int max = theStream.max().orElse(Level.DELAY);
        if (max == Level.DELAY && !isHasBeenDefined()) {
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
            case MODIFIED: {
                // if the parameter is level 2 immutable, it cannot be modified
                Boolean e2immu = parameterInfo.parameterizedType.isAtLeastEventuallyE2Immutable();
                if (e2immu == Boolean.TRUE) return Level.FALSE;
                if (parameterInfo.parameterizedType.isFunctionalInterface()) {
                    return Level.FALSE; // by definition, see manual
                }
                if (!parameterInfo.owner.isPrivate() &&
                        analysisProvider.getTypeAnalysis(parameterInfo.owner.typeInfo).getProperty(VariableProperty.CONTAINER) == Level.TRUE) {
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
                    int immutable = analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
                    boolean objectFlowCondition = parameterInfo.owner.isPrivate() &&
                            objectFlow != null && objectFlow.getPrevious().allMatch(of -> of.conditionsMetForEventual(bestType));
                    immutableFromType = MultiLevel.eventual(immutable, objectFlowCondition);
                } else {
                    immutableFromType = MultiLevel.MUTABLE;
                }
                return Math.max(immutableFromType, MultiLevel.delayToFalse(internalGetProperty(VariableProperty.IMMUTABLE)));
            }

            case NOT_NULL: {
                TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
                if (bestType != null && bestType.isPrimitive()) return MultiLevel.EFFECTIVELY_NOT_NULL;
                return getParameterPropertyCheckOverrides(AnalysisProvider.DEFAULT_PROVIDER, parameterInfo, variableProperty);
            }

            case SIZE:
                return getParameterPropertyCheckOverrides(AnalysisProvider.DEFAULT_PROVIDER, parameterInfo, variableProperty);

            case NOT_MODIFIED_1:
                if (!parameterInfo.parameterizedType.isFunctionalInterface()) {
                    return Level.FALSE;
                }
                break;

            default:
        }
        return internalGetProperty(variableProperty);
    }

}
