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
                .map(ParameterAnalysis::createEmpty).collect(Collectors.toList());
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
    Function<Integer, Integer> OVERRIDE_EFFECTIVELY_NOT_NULL = x -> MultiLevel.EFFECTIVELY_NOT_NULL;
    Function<Integer, Integer> NO_INFLUENCE = x -> x;

    default Function<Integer, Integer> influenceOfType(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        MethodInfo methodInfo = getMethodInfo();
        ParameterizedType returnType = methodInfo.returnType();

        switch (variableProperty) {
            case MODIFIED_METHOD:
                TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(methodInfo.typeInfo);
                if (!methodInfo.isConstructor &&
                        !methodInfo.isAbstract() &&
                        typeAnalysis.getProperty(VariableProperty.IMMUTABLE) == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                    return OVERRIDE_FALSE;
                }
                return NO_INFLUENCE;

            case NOT_NULL_EXPRESSION:
                if (Primitives.isPrimitiveExcludingVoid(returnType)) return OVERRIDE_EFFECTIVELY_NOT_NULL;
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

    default int getMethodProperty(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        return switch (variableProperty) {
            case MODIFIED_METHOD, FLUENT, IDENTITY, INDEPENDENT, NOT_NULL_EXPRESSION, CONTAINER -> getPropertyCheckOverrides(analysisProvider, variableProperty);
            default -> internalGetProperty(variableProperty);
        };
    }

    private int getPropertyCheckOverrides(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        int mineAsIs = getPropertyAsIs(variableProperty);
        int mine = influenceOfType(analysisProvider, variableProperty).apply(mineAsIs);
        int max;
        if (getMethodInfo().shallowAnalysis()) {
            int bestOfOverrides = Level.DELAY;
            for (MethodAnalysis override : getOverrides(analysisProvider)) {
                int overrideAsIs = override.getPropertyAsIs(variableProperty);
                int combinedWithType = override.influenceOfType(analysisProvider, variableProperty).apply(overrideAsIs);
                bestOfOverrides = Math.max(bestOfOverrides, combinedWithType);
            }
            max = Math.max(mine, bestOfOverrides);
        } else {
            max = mine;
        }
        if (max == Level.DELAY && getMethodInfo().shallowAnalysis()) {
            // no information found in the whole hierarchy, we default to the value of the annotation mode

            // unless: abstract methods, not annotated for modification. They remain as they are
            if (variableProperty == VariableProperty.MODIFIED_METHOD && getMethodInfo().isAbstract()) {
                return Level.DELAY;
            }
            return variableProperty.valueWhenAbsent(annotationMode());
        }
        return max;
    }

    default int valueFromOverrides(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        Set<MethodAnalysis> overrides = getOverrides(analysisProvider);
        return overrides.stream()
                .mapToInt(ma -> ma.getPropertyAsIs(variableProperty)).max().orElse(Level.DELAY);
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
