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
     * @return never null; can be delayed
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
     * @return delayed when not yet computed, EMPTY when no precondition
     */
    Precondition getPrecondition();

    default MethodLevelData methodLevelData() {
        StatementAnalysis last = getLastStatement();
        if (last == null) return null; // there is no last statement --> there are no statements
        return last.methodLevelData;
    }

    default DV getMethodProperty(Property property) {
        return switch (property) {
            case CONTAINER, IMMUTABLE, NOT_NULL_EXPRESSION, MODIFIED_METHOD, TEMP_MODIFIED_METHOD,
                    FLUENT, IDENTITY, INDEPENDENT, CONSTANT, FINALIZER -> getPropertyFromMapDelayWhenAbsent(property);
            default -> throw new PropertyException(Analyser.AnalyserIdentification.METHOD, property);
        };
    }

    default DV valueFromOverrides(AnalysisProvider analysisProvider, Property property) {
        Set<MethodAnalysis> overrides = getOverrides(analysisProvider);
        return overrides.stream()
                .map(ma -> ma.getPropertyFromMapDelayWhenAbsent(property))
                .reduce(Level.NOT_INVOLVED_DV, DV::max);
    }

    default boolean eventualIsSet() {
        return true;
    }

    Eventual NOT_EVENTUAL = new Eventual(CausesOfDelay.EMPTY, Set.of(), false, null, null);

    CausesOfDelay preconditionForEventualStatus();

    CausesOfDelay eventualStatus();

    CausesOfDelay preconditionStatus();

    static Eventual delayedEventual(CausesOfDelay causes) {
        return new Eventual(causes, Set.of(), false, null, null);
    }

    record Eventual(CausesOfDelay causesOfDelay,
                    Set<FieldInfo> fields,
                    boolean mark,
                    Boolean after, // null for a @Mark without @Only
                    Boolean test) { // true for isSet (before==false), false for !isSet (before==true), null for absent

        public Eventual {
            assert !fields.isEmpty() || !mark && after == null && test == null;
        }

        public Eventual(Set<FieldInfo> fields, boolean mark, Boolean after, Boolean test) {
            this(CausesOfDelay.EMPTY, fields, mark, after, test);
        }

        @Override
        public String toString() {
            if (mark) return "@Mark: " + fields;
            if (test != null) return "@TestMark: " + fields;
            if (after == null) {
                if (this == NOT_EVENTUAL) return "NOT_EVENTUAL";
           //     if (this == DELAYED_EVENTUAL) return "DELAYED_EVENTUAL";
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
