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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public interface MethodAnalysis extends Analysis {

    static MethodAnalysis createEmpty(MethodInfo methodInfo) {
        List<ParameterAnalysis> parameterAnalyses = methodInfo.methodInspection.get().getParameters().stream()
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
    default Expression getSingleReturnValue() {
        return EmptyExpression.NO_VALUE;
    }

    default Set<MethodAnalysis> getOverrides(AnalysisProvider analysisProvider) {
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
        throw new UnsupportedOperationException(); // needs an implementation!
    }

    // the value here (size will be one)
    default List<Expression> getPreconditionForMarkAndOnly() {
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

    default Map<CompanionMethodName, CompanionAnalysis> getCompanionAnalyses() {
        return null;
    }

    default  Map<CompanionMethodName, MethodInfo> getComputedCompanions() {
        return null;
    }

    // ************** PRECONDITION

    /**
     * @return null when not yet computed, EMPTY when no precondition
     */
    default Expression getPrecondition() {
        return EmptyExpression.EMPTY_EXPRESSION;
    }

    default MethodLevelData methodLevelData() {
        StatementAnalysis last = getLastStatement();
        if (last == null) return null; // there is no last statement --> there are no statements
        return last.methodLevelData;
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
                return getPropertyCheckOverrides(analysisProvider, VariableProperty.MODIFIED);

            case FLUENT:
            case IDENTITY:

            case INDEPENDENT:
                // TODO if we have an array constructor created on-the-fly, it should be EFFECTIVELY INDEPENDENT
                return getPropertyCheckOverrides(analysisProvider, variableProperty);

            case NOT_NULL:
                if (Primitives.isPrimitiveExcludingVoid(returnType)) return MultiLevel.EFFECTIVELY_NOT_NULL;
                int fluent = getProperty(VariableProperty.FLUENT);
                if (fluent == Level.TRUE) return MultiLevel.EFFECTIVELY_NOT_NULL;
                return getPropertyCheckOverrides(analysisProvider, VariableProperty.NOT_NULL);

            case IMMUTABLE:
                assert returnType != ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR : "void method";
                int immutableType = returnType.getProperty(analysisProvider, VariableProperty.IMMUTABLE);
                int immutableDynamic = dynamicProperty(analysisProvider, immutableType, returnType);
                return MultiLevel.bestImmutable(immutableType, immutableDynamic);

            case CONTAINER:
                assert returnType != ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR : "void method";
                int container = returnType.getProperty(analysisProvider, VariableProperty.CONTAINER);
                if (container == Level.DELAY) return Level.DELAY;
                return Level.best(getPropertyCheckOverrides(analysisProvider, VariableProperty.CONTAINER), container);

            default:
        }
        return internalGetProperty(variableProperty);
    }

    private int dynamicProperty(AnalysisProvider analysisProvider, int formalImmutableProperty, ParameterizedType returnType) {
        int immutableTypeAfterEventual = MultiLevel.eventual(formalImmutableProperty,
                getObjectFlow().conditionsMetForEventual(analysisProvider, returnType));
        return Level.best(internalGetProperty(VariableProperty.IMMUTABLE), immutableTypeAfterEventual);
    }

    default int valueFromOverrides(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        return getOverrides(analysisProvider).stream()
                .mapToInt(ma -> ma.getPropertyAsIs(variableProperty)).max().orElse(Level.DELAY);
    }

    private int getPropertyCheckOverrides(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        IntStream mine = IntStream.of(getPropertyAsIs(variableProperty));
        IntStream theStream;
        if (getMethodInfo().shallowAnalysis()) {
            IntStream overrideValues = getOverrides(analysisProvider).stream().mapToInt(ma -> ma.getPropertyAsIs(variableProperty));
            theStream = IntStream.concat(mine, overrideValues);
        } else {
            theStream = mine;
        }
        int max = theStream.max().orElse(Level.DELAY);
        if (max == Level.DELAY && getMethodInfo().shallowAnalysis()) {
            // no information found in the whole hierarchy
            return variableProperty.valueWhenAbsent(annotationMode());
        }
        return max;
    }

    // the name refers to the @Mark and @Only annotations. It is the data for this annotation.

    record MarkAndOnly(List<Expression> preconditions,String markLabel, boolean mark,Boolean after) { // null for a @Mark without @Only

        @Override
        public String toString() {
            return markLabel + "=" + preconditions + "; after? " + after + "; @Mark? " + mark;
        }
    }

}
