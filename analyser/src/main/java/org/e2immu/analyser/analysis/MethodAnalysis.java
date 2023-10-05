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

package org.e2immu.analyser.analysis;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.CommutableData;
import org.e2immu.analyser.util.ParSeq;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface MethodAnalysis extends Analysis {

    @NotNull
    MethodInfo getMethodInfo();

    /**
     * @return null when not (yet) decided
     */
    Expression getSingleReturnValue();

    default Set<MethodAnalysis> getOverrides(AnalysisProvider analysisProvider, boolean complainIfNotAnalyzed) {
        return Set.of();
    }

    /**
     * @return null when the method is not defined (has no statements)
     */
    default StatementAnalysis getFirstStatement() {
        return null;
    }

    @NotNull(content = true)
    List<ParameterAnalysis> getParameterAnalyses();

    /**
     * @return null when the method is not defined (has no statements)
     */
    default StatementAnalysis getLastStatement() {
        return getLastStatement(false);
    }

    default StatementAnalysis getLastStatement(boolean excludeThrows) {
        throw new UnsupportedOperationException();
    }

    // the value here (size will be one)
    Precondition getPreconditionForEventual();

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
     * @return delayed See org.e2immu.analyser.analysis.StateData#setPrecondition(org.e2immu.analyser.analyser.Precondition, boolean)
     * for a description of the conventions.
     */
    @NotNull
    Precondition getPrecondition();

    /**
     * @return post-conditions, in no particular order.
     */
    @NotNull
    Set<PostCondition> getPostConditions();

    /*
    Many throw and assert statements find their way into a pre- or post-condition.
    Some, however, do not. We register them here.
     */
    @NotNull
    Set<String> indicesOfEscapesNotInPreOrPostConditions();

    CommutableData getCommutableData();

    default MethodLevelData methodLevelData() {
        StatementAnalysis last = getLastStatement();
        if (last == null) return null; // there is no last statement --> there are no statements
        return last.methodLevelData();
    }

    default DV getMethodProperty(Property property) {
        return switch (property) {
            case MODIFIED_METHOD_ALT_TEMP -> modifiedMethodOrTempModifiedMethod();
            case CONTAINER, IMMUTABLE, IMMUTABLE_BREAK, NOT_NULL_EXPRESSION, TEMP_MODIFIED_METHOD, MODIFIED_METHOD,
                    FLUENT, IDENTITY, IGNORE_MODIFICATIONS, INDEPENDENT, CONSTANT, STATIC_SIDE_EFFECTS ->
                    getPropertyFromMapDelayWhenAbsent(property);
            case FINALIZER -> getPropertyFromMapNeverDelay(property);
            default -> throw new PropertyException(Analyser.AnalyserIdentification.METHOD, property);
        };
    }

    private DV modifiedMethodOrTempModifiedMethod() {
        DV mm = getPropertyFromMapDelayWhenAbsent(Property.MODIFIED_METHOD);
        if (mm.isDelayed() && getMethodInfo().methodResolution.get().partOfCallCycle()) {
            return getPropertyFromMapDelayWhenAbsent(Property.TEMP_MODIFIED_METHOD);
        }
        return mm;
    }

    default DV valueFromOverrides(AnalysisProvider analysisProvider, Property property) {
        Set<MethodAnalysis> overrides = getOverrides(analysisProvider, true);
        return overrides.stream()
                .map(ma -> ma.getPropertyFromMapDelayWhenAbsent(property))
                .reduce(DelayFactory.initialDelay(), DV::max);
    }

    default boolean eventualIsSet() {
        return true;
    }

    Eventual NOT_EVENTUAL = new Eventual(CausesOfDelay.EMPTY, Set.of(), false, null, null);

    CausesOfDelay eventualStatus();

    CausesOfDelay preconditionStatus();

    static Eventual delayedEventual(CausesOfDelay causes) {
        return new Eventual(causes, Set.of(), false, null, null);
    }

    // associated with the @Commutable annotation
    ParSeq<ParameterInfo> getParallelGroups();

    default boolean hasParallelGroups() {
        ParSeq<ParameterInfo> parSeq = getParallelGroups();
        return parSeq != null && parSeq.containsParallels();
    }

    List<Expression> sortAccordingToParallelGroupsAndNaturalOrder(List<Expression> parameterExpressions);

    default String postConditionsSortedToString() {
        return getPostConditions().stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
    }

    record Eventual(CausesOfDelay causesOfDelay,
                    Set<FieldInfo> fields,
                    boolean mark,
                    Boolean after, // null for a @Mark without @Only
                    Boolean test) { // true for isSet (before==false, after==true), false for !isSet (before==true, after==false), null for absent

        public Eventual {
            assert !fields.isEmpty() || !mark && after == null && test == null;
        }

        public Eventual(Set<FieldInfo> fields, boolean mark, Boolean after, Boolean test) {
            this(CausesOfDelay.EMPTY, fields, mark, after, test);
        }

        @Override
        public String toString() {
            if (causesOfDelay.isDelayed()) {
                return "[DelayedEventual:" + causesOfDelay + "]";
            }
            if (mark) return "@Mark: " + fields;
            if (test != null) return "@TestMark: " + (test ? "" : "!") + fields;
            if (after == null) {
                if (this == NOT_EVENTUAL) return "NOT_EVENTUAL";
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

        public boolean isOnly() {
            assert causesOfDelay.isDone();
            return !mark && test == null;
        }

        public boolean isMark() {
            assert causesOfDelay.isDone();
            return mark;
        }

        public boolean isTestMark() {
            assert causesOfDelay.isDone();
            return test != null;
        }
    }

    default List<VariableInfo> getFieldAsVariable(FieldInfo fieldInfo) {
        StatementAnalysis lastStatement = getLastStatement();
        return lastStatement == null ? List.of() : getLastStatement().latestInfoOfVariablesReferringTo(fieldInfo);
    }

    default List<VariableInfo> getFieldAsVariableAssigned(FieldInfo fieldInfo) {
        return getFieldAsVariable(fieldInfo).stream().filter(VariableInfo::isAssigned)
                .toList();
    }

    void markFirstIteration();

    boolean hasBeenAnalysedUpToIteration0();

    FieldInfo getSetField();

    record GetSetEquivalent(MethodInfo methodInfo, Set<ParameterInfo> convertToGetSet) {}
    GetSetEquivalent getSetEquivalent();
}
