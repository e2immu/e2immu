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
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public interface MethodAnalysis extends Analysis {

    static MethodAnalysis createEmpty(MethodInfo methodInfo, Primitives primitives) {
        List<ParameterAnalysis> parameterAnalyses = methodInfo.methodInspection.get().getParameters().stream()
                .map(ParameterAnalysis::createEmpty).collect(Collectors.toList());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(false,
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

    default Set<ObjectFlow> getInternalObjectFlows() {
        return Set.of();
    }

    default ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW;
    }

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

    default int getMethodProperty(AnalysisProvider analysisProvider, MethodInfo methodInfo, VariableProperty variableProperty) {
        ParameterizedType returnType = methodInfo.returnType();
        switch (variableProperty) {
            case MODIFIED_METHOD:
                // all methods in java.lang.String are @NotModified, but we do not bother writing that down
                // we explicitly check on EFFECTIVE, because in an eventually E2IMMU we want the methods to remain @Modified
                TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(methodInfo.typeInfo);
                if (!methodInfo.isConstructor &&
                        !methodInfo.isAbstract() &&
                        typeAnalysis.getProperty(VariableProperty.IMMUTABLE) == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                    return Level.FALSE;
                }
                return getPropertyCheckOverrides(analysisProvider, VariableProperty.MODIFIED_METHOD);

            case FLUENT:
            case IDENTITY:

            case INDEPENDENT:
                // TODO if we have an array constructor created on-the-fly, it should be EFFECTIVELY INDEPENDENT
                return getPropertyCheckOverrides(analysisProvider, variableProperty);

            case NOT_NULL_EXPRESSION:
                if (Primitives.isPrimitiveExcludingVoid(returnType)) return MultiLevel.EFFECTIVELY_NOT_NULL;
                int fluent = getProperty(VariableProperty.FLUENT);
                if (fluent == Level.TRUE) return MultiLevel.EFFECTIVELY_NOT_NULL;
                return getPropertyCheckOverrides(analysisProvider, VariableProperty.NOT_NULL_EXPRESSION);

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
                getObjectFlow().conditionsMetForEventual(getMethodInfo().typeInfo, InspectionProvider.DEFAULT, analysisProvider, returnType));
        return Level.best(internalGetProperty(VariableProperty.IMMUTABLE), immutableTypeAfterEventual);
    }

    default int valueFromOverrides(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        Set<MethodAnalysis> overrides = getOverrides(analysisProvider);
        return overrides.stream()
                .mapToInt(ma -> ma.getPropertyAsIs(variableProperty)).max().orElse(Level.DELAY);
    }

    private int getPropertyCheckOverrides(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        int mine = getPropertyAsIs(variableProperty);
        int max;
        if (getMethodInfo().shallowAnalysis()) {
            int bestOfOverrides = Level.DELAY;
            for (MethodAnalysis override : getOverrides(analysisProvider)) {
                int overrideAsIs = override.getPropertyAsIs(variableProperty);
                bestOfOverrides = Math.max(bestOfOverrides, overrideAsIs);
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
    }

}
