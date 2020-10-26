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
import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public interface MethodAnalysis extends Analysis {

    static MethodAnalysis createEmpty(MethodInfo methodInfo) {
        List<ParameterAnalysis> parameterAnalyses = methodInfo.methodInspection.get().parameters.stream()
                .map(ParameterAnalysis::createEmpty).collect(Collectors.toList());
        return new MethodAnalysis() {
            @Override
            public MethodInfo getMethodInfo() {
                return methodInfo;
            }

            @Override
            public List<ParameterAnalysis> getParameterAnalyses() {
                return parameterAnalyses;
            }

            @Override
            public Location location() {
                return new Location(methodInfo);
            }
        };
    }

    MethodInfo getMethodInfo();

    /**
     * @return null when not yet decided
     */
    default Value getSingleReturnValue() {
        return UnknownValue.NO_VALUE;
    }

    default Set<MethodAnalysis> getOverrides() {
        return Set.of();
    }

    /**
     * @return null when the method is not defined (has no statements)
     */
    default StatementAnalysis getFirstStatement() {
        return null;
    }

    List<ParameterAnalysis> getParameterAnalyses();

    /**
     * @return null when the method is not defined (has no statements)
     */
    default StatementAnalysis getLastStatement() {
        return null;
    }

    // the value here (size will be one)
    default List<Value> getPreconditionForMarkAndOnly() {
        return List.of();
    }

    /**
     * @return null when the method has no MarkAndOnly
     */
    default MarkAndOnly getMarkAndOnly() {
        return null;
    }

    // ************* object flow

    default Set<ObjectFlow> getInternalObjectFlows() {
        return Set.of();
    }

    default ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW;
    }

    default Boolean getComplainedAboutMissingStaticModifier() {
        return null;
    }

    default Boolean getComplainedAboutApprovedPreconditions() {
        return null;
    }


    // replacements

    // set when all replacements have been done


    // ************** PRECONDITION

    /**
     * @return null when not yet computed, EMPTY when no precondition
     */
    default Value getPrecondition() {
        return UnknownValue.EMPTY;
    }

    default MethodLevelData methodLevelData() {
        StatementAnalysis last = getLastStatement();
        return last != null ? last.methodLevelData : getFirstStatement().lastStatement().methodLevelData;
    }

    default int getMethodProperty(AnalysisProvider analysisProvider, MethodInfo methodInfo, VariableProperty variableProperty) {
        ParameterizedType returnType = methodInfo.returnType();
        switch (variableProperty) {
            case MODIFIED:
                // all methods in java.lang.String are @NotModified, but we do not bother writing that down
                // we explicitly check on EFFECTIVE, because in an eventually E2IMMU we want the methods to remain @Modified
                TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(methodInfo.typeInfo);
                if (!methodInfo.isConstructor &&
                        typeAnalysis.getProperty(VariableProperty.IMMUTABLE) == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                    return Level.FALSE;
                }
                return getPropertyCheckOverrides(VariableProperty.MODIFIED);

            case FLUENT:
            case IDENTITY:
            case SIZE:
                return getPropertyCheckOverrides(variableProperty);

            case INDEPENDENT:
                // TODO if we have an array constructor created on-the-fly, it should be EFFECTIVELY INDEPENDENT
                return getPropertyCheckOverrides(variableProperty);

            case NOT_NULL:
                if (returnType.isPrimitive()) return MultiLevel.EFFECTIVELY_NOT_NULL;
                int fluent = getProperty(VariableProperty.FLUENT);
                if (fluent == Level.TRUE) return MultiLevel.EFFECTIVELY_NOT_NULL;
                return getPropertyCheckOverrides(VariableProperty.NOT_NULL);

            case IMMUTABLE:
                if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR || returnType.isVoid())
                    throw new UnsupportedOperationException(); //we should not even be asking

                int immutableType = formalProperty(returnType);
                int immutableDynamic = dynamicProperty(immutableType, returnType);
                return MultiLevel.bestImmutable(immutableType, immutableDynamic);

            case CONTAINER:
                if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR || returnType.isVoid())
                    throw new UnsupportedOperationException(); //we should not even be asking
                int container = returnType.getProperty(VariableProperty.CONTAINER);
                if (container == Level.DELAY) return Level.DELAY;
                return Level.best(getPropertyCheckOverrides(VariableProperty.CONTAINER), container);

            default:
        }
        return internalGetProperty(variableProperty);
    }

    private int dynamicProperty(int formalImmutableProperty, ParameterizedType returnType) {
        int immutableTypeAfterEventual = MultiLevel.eventual(formalImmutableProperty, getObjectFlow().conditionsMetForEventual(returnType));
        return Level.best(internalGetProperty(VariableProperty.IMMUTABLE), immutableTypeAfterEventual);
    }

    private static int formalProperty(ParameterizedType returnType) {
        return returnType.getProperty(VariableProperty.IMMUTABLE);
    }

    default int valueFromOverrides(VariableProperty variableProperty) {
        return getOverrides().stream().mapToInt(ma -> ma.getPropertyAsIs(variableProperty)).max().orElse(Level.DELAY);
    }

    private int getPropertyCheckOverrides(VariableProperty variableProperty) {
        IntStream mine = IntStream.of(getPropertyAsIs(variableProperty));
        IntStream theStream;
        if (isHasBeenDefined()) {
            theStream = mine;
        } else {
            IntStream overrideValues = getOverrides().stream().mapToInt(ma -> ma.getPropertyAsIs(variableProperty));
            theStream = IntStream.concat(mine, overrideValues);
        }
        int max = theStream.max().orElse(Level.DELAY);
        if (max == Level.DELAY && !isHasBeenDefined()) {
            // no information found in the whole hierarchy
            return variableProperty.valueWhenAbsent(annotationMode());
        }
        return max;
    }

    private static boolean allowIndependentOnMethod(ParameterizedType returnType, TypeAnalysis typeAnalysis) {
        return !returnType.isVoid() && returnType.isImplicitlyOrAtLeastEventuallyE2Immutable(typeAnalysis) != Boolean.TRUE;
    }

    // the name refers to the @Mark and @Only annotations. It is the data for this annotation.

    class MarkAndOnly {
        public final List<Value> preconditions;
        public final String markLabel;
        public final boolean mark;
        public final Boolean after; // null for a @Mark without @Only

        public MarkAndOnly(List<Value> preconditions, String markLabel, boolean mark, Boolean after) {
            this.preconditions = preconditions;
            this.mark = mark;
            this.markLabel = markLabel;
            this.after = after;
        }

        @Override
        public String toString() {
            return markLabel + "=" + preconditions + "; after? " + after + "; @Mark? " + mark;
        }
    }

}
