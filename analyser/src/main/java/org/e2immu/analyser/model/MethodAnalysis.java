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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface MethodAnalysis extends Analysis {

    static MethodAnalysis createEmpty(MethodInfo methodInfo, Primitives primitives) {
        List<ParameterAnalysis> parameterAnalyses = methodInfo.methodInspection.get().getParameters().stream()
                .map(p -> (ParameterAnalysis) new ParameterAnalysisImpl.Builder(primitives, AnalysisProvider.DEFAULT_PROVIDER, p).build())
                .collect(Collectors.toList());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(AnalysisMode.CONTRACTED,
                primitives, AnalysisProvider.DEFAULT_PROVIDER, InspectionProvider.DEFAULT,
                methodInfo, parameterAnalyses);
        return (MethodAnalysis) builder.build();
    }

    MethodInfo getMethodInfo();

    /**
     * @return null when not (yet) decided
     */
    default Expression getSingleReturnValue() {
        return null;
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
    default Precondition getPreconditionForEventual() {
        return null;
    }

    /**
     * @return null when the method has no eventual
     */
    default Eventual getEventual() {
        throw new UnsupportedOperationException();
    }

    // ************* object flow

    default Map<CompanionMethodName, CompanionAnalysis> getCompanionAnalyses() {
        return null;
    }

    default Map<CompanionMethodName, MethodInfo> getComputedCompanions() {
        return null;
    }

    // ************** PRECONDITION

    /**
     * @return null when not yet computed, EMPTY when no precondition
     */
    default Precondition getPrecondition() {
        return null;
    }

    default MethodLevelData methodLevelData() {
        StatementAnalysis last = getLastStatement();
        if (last == null) return null; // there is no last statement --> there are no statements
        return last.methodLevelData;
    }

    Function<Integer, Integer> OVERRIDE_FALSE = x -> Level.FALSE;
    Function<Integer, Integer> OVERRIDE_TRUE = x -> Level.TRUE;
    Function<Integer, Integer> OVERRIDE_EFFECTIVELY_NOT_NULL = x -> MultiLevel.EFFECTIVELY_NOT_NULL;
    Function<Integer, Integer> NO_INFLUENCE = x -> x;

    default Function<Integer, Integer> influenceOfType(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        MethodInfo methodInfo = getMethodInfo();
        ParameterizedType returnType = methodInfo.returnType();

        switch (variableProperty) {
            case INDEPENDENT:
                if (methodInfo.isConstructor) {
                    int worstOverParameters = methodInfo.methodInspection.get().getParameters().stream()
                            .mapToInt(pi -> analysisProvider.getParameterAnalysis(pi)
                                    .getParameterProperty(analysisProvider, pi, VariableProperty.INDEPENDENT))
                            .min().orElse(MultiLevel.INDEPENDENT);
                    if (worstOverParameters > Level.DELAY) return x -> worstOverParameters;
                }
                return NO_INFLUENCE;

            case MODIFIED_METHOD:
                if (methodInfo.isConstructor) return OVERRIDE_TRUE; // by definition
                TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(methodInfo.typeInfo);
                // keep this isAbstract check! See PropagateModification_1;
                // a type can be @E2Immutable with an abstract method,
                // which can still have MODIFIED_METHOD == Level.DELAY rather than Level.FALSE
                if (!methodInfo.isAbstract() &&
                        typeAnalysis.getProperty(VariableProperty.IMMUTABLE) == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                    return OVERRIDE_FALSE;
                }
                return NO_INFLUENCE;

            case NOT_NULL_EXPRESSION:
                int fluent = getProperty(VariableProperty.FLUENT);
                if (fluent == Level.TRUE) return OVERRIDE_EFFECTIVELY_NOT_NULL;
                return NO_INFLUENCE;

            case CONTAINER:
                if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR) return NO_INFLUENCE;
                int container = returnType.getProperty(analysisProvider, VariableProperty.CONTAINER);
                return inMethod -> Math.max(inMethod, container);

            default:
                return NO_INFLUENCE;
        }
    }

    /*
    Note that we do not have to make a distinction between explicitly empty methods and shallow analysis methods.
    The ShallowMethodAnalyser sets explicit values for this case.
     */
    default int getMethodProperty(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        int propertyFromType = ImplicitProperties.fromType(getMethodInfo().returnType(), variableProperty);
        if (propertyFromType > Level.DELAY) return propertyFromType;

        return switch (variableProperty) {
            case MODIFIED_METHOD, FLUENT, IDENTITY, INDEPENDENT, NOT_NULL_EXPRESSION, CONSTANT, CONTAINER, FINALIZER, IMMUTABLE -> getPropertyCheckOverrides(analysisProvider, variableProperty);
            default -> throw new PropertyException(Analyser.AnalyserIdentification.METHOD, variableProperty);
        };
    }

    private int getPropertyCheckOverrides(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        boolean shallow = getMethodInfo().shallowAnalysis();
        int mineAsIs = getPropertyFromMapDelayWhenAbsent(variableProperty);
        int influencedByType = influenceOfType(analysisProvider, variableProperty).apply(mineAsIs);
        if (!shallow) return influencedByType;

        // only for shallow methods now

        int bestOfOverrides = Level.DELAY;
        for (MethodAnalysis override : getOverrides(analysisProvider)) {
            int overrideAsIs = override.getPropertyFromMapDelayWhenAbsent(variableProperty);
            int combinedWithType = override.influenceOfType(analysisProvider, variableProperty).apply(overrideAsIs);
            bestOfOverrides = Math.max(bestOfOverrides, combinedWithType);
        }
        int max = Math.max(influencedByType, bestOfOverrides);

        if (max == Level.DELAY && getMethodInfo().isAbstract()) {

            // unless: abstract methods, not annotated for modification
            if (variableProperty == VariableProperty.MODIFIED_METHOD) {
                /*
                 In case of a shallow type: if you mark a shallow type as level 2 immutable then its abstract methods are @NotModified by default
                 See Basics_5, Stream is @E2Container so filter, findAny, map etc. must be @NotModified.
                 Otherwise, the method is shallow but the type is not. See e.g. PropagateModification_8.
                 If the type is found to be @E2Immutable, then it makes little sense to mark the method as @Modified,
                 so we choose @NotModified instead.
                */
                TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(getMethodInfo().typeInfo);
                int immutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
                if (immutable == Level.DELAY) {
                    return Level.DELAY;
                }
                if (immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                    return Level.FALSE;
                }
            }
            return variableProperty.valueWhenAbsent();
        }
        return max;
    }

    default int valueFromOverrides(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        Set<MethodAnalysis> overrides = getOverrides(analysisProvider);
        return overrides.stream()
                .mapToInt(ma -> ma.getPropertyFromMapDelayWhenAbsent(variableProperty)).max().orElse(Level.DELAY);
    }

    default boolean eventualIsSet() {
        return true;
    }

    Eventual NOT_EVENTUAL = new Eventual(Set.of(), false, null, null);
    Eventual DELAYED_EVENTUAL = new Eventual(Set.of(), false, null, null);

    record Eventual(Set<FieldInfo> fields,
                    boolean mark,
                    Boolean after, // null for a @Mark without @Only
                    Boolean test) { // true for isSet (before==false), false for !isSet (before==true), null for absent

        public Eventual {
            assert !fields.isEmpty() || !mark && after == null && test == null;
        }

        @Override
        public String toString() {
            if (mark) return "@Mark: " + fields;
            if (test != null) return "@TestMark: " + fields;
            if (after == null) {
                if (this == NOT_EVENTUAL) return "NOT_EVENTUAL";
                if (this == DELAYED_EVENTUAL) return "DELAYED_EVENTUAL";
                throw new UnsupportedOperationException();
            }
            return "@Only " + (after ? "after" : "before") + ": " + fields;
        }

        public String markLabel() {
            return fields.stream().map(f -> f.name).sorted().collect(Collectors.joining(","));
        }

        public boolean consistentWith(Eventual other) {
            return fields.equals(other.fields);
        }

        public boolean notMarkOrBefore() {
            if (mark) return false;
            if (test != null) return true;
            if (after == null) return true;
            return after;
        }
    }

}
