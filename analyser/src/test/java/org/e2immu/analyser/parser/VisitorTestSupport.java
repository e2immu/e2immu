
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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.range.Range;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.visitor.CommonVisitorData;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public abstract class VisitorTestSupport {

    protected void assertSubMap(Map<AnalysisStatus, Set<String>> expect, Map<String, AnalysisStatus> statuses) {
        expect.forEach((as, set) -> set.forEach(label -> assertEquals(as, statuses.get(label),
                "Expected " + as + " for " + label + "; map is\n" + statuses)));
    }

    public void assertDvInitial(StatementAnalyserVariableVisitor.Data d, DV expect, Property property) {
        DV value = d.variableInfoContainer().getPreviousOrInitial().getProperty(property);
        assertEquals(expect, value);
    }

    public void assertDv(CommonVisitorData d, DV expect, Property property) {
        DV value = d.getProperty(property);
        assertEquals(expect, value);
    }

    public void assertDv(CommonVisitorData d, DV expect, Property property, String message) {
        DV value = d.getProperty(property);
        assertEquals(expect, value, message);
    }

    public void assertDvInitial(StatementAnalyserVariableVisitor.Data d, String delayed, int delayedBeforeIteration, DV expect, Property property) {
        DV value = d.variableInfoContainer().getPreviousOrInitial().getProperty(property);
        if (d.iteration() < delayedBeforeIteration) {
            assertEquals(delayed, value.causesOfDelay().toString(), value.isDone() ? "Expected delay in iteration " + d.iteration() + "<=" + delayedBeforeIteration + ", but got " + value + " for property " + property :
                    "Expected delay " + delayed + ", but got " + value + " in iteration " + d.iteration() + "<" + delayedBeforeIteration + " for property " + property);
        } else {
            assertEquals(expect, value, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        }
        mustSeeIteration(d, delayedBeforeIteration);
    }

    public void assertDv(CommonVisitorData d, int delayedBeforeIteration, DV expect, Property property) {
        DV value = d.getProperty(property);
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(value == null || value.isDelayed(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        } else {
            assertEquals(expect, value, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        }
        mustSeeIteration(d, delayedBeforeIteration);
    }

    public void assertDv(CommonVisitorData d, String delayed, int delayedBeforeIteration, DV expect, Property property) {
        DV value = d.getProperty(property);
        if (d.iteration() < delayedBeforeIteration) {
            assertNotNull(value, "Expect delay rather than null in iteration " + d.iteration()
                    + "<" + delayedBeforeIteration + " for property " + property);
            assertEquals(delayed, value.toString(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        } else {
            assertEquals(expect, value, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        }
        mustSeeIteration(d, delayedBeforeIteration);
    }

    public void assertDv(StatementAnalyserVisitor.Data d, int delayedBeforeIteration, DV expect, DV actual) {
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(actual.isDelayed(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + actual);
        } else {
            assertEquals(expect, actual, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + actual);
        }
        mustSeeIteration(d, delayedBeforeIteration);
    }

    public void assertDv(StatementAnalyserVisitor.Data d, int delayedBeforeIteration, AnalysisStatus expect, AnalysisStatus actual) {
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(actual.isDelayed(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + actual);
        } else {
            assertEquals(expect, actual, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + actual);
        }
        mustSeeIteration(d, delayedBeforeIteration);
    }

    public void assertCurrentValue(StatementAnalyserVariableVisitor.Data d, int delayedBeforeIteration, String causesOfDelay, String value) {
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(d.currentValue().isDelayed(), "Expected current value to be delayed in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but was " + d.currentValue() + " for variable " + d.variableName());
            assertEquals(extract(causesOfDelay, d.iteration()), d.currentValue().causesOfDelay().toString());
        } else {
            assertTrue(d.currentValue().isDone(), "Expected current value to be done in iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + d.currentValue()
                    .causesOfDelay() + " for variable " + d.variableName());
            assertEquals(value, d.currentValue().toString());
        }
        mustSeeIteration(d, delayedBeforeIteration);
    }

    public void assertModificationTime(StatementAnalyserVariableVisitor.Data d, int delayedBeforeIteration, int value) {
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(d.variableInfo().getModificationTimeOrNegative() < 0, "Expected negative modification time");
        } else {
            assertEquals(value, d.variableInfo().getModificationTimeOrNegative());
        }
    }

    public void assertValue(StatementAnalyserVariableVisitor.Data d, String s0, String s1, String s) {
        String value = d.currentValue().toString();
        if (d.iteration() == 0) assertEquals(s0, value);
        else if (d.iteration() == 1) assertEquals(s1, value);
        else assertEquals(s, value);
    }

    public void assertCurrentValue(StatementAnalyserVariableVisitor.Data d, int delayedBeforeIteration, String value) {
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(d.currentValue().isDelayed(), "Expected current value to be delayed in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but was " + d.currentValue() + " for variable " + d.variableName());
        } else {
            assertTrue(d.currentValue().isDone(), "Expected current value to be done in iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + d.currentValue()
                    .causesOfDelay() + " for variable " + d.variableName());
            assertEquals(value, d.currentValue().toString());
        }
        mustSeeIteration(d, delayedBeforeIteration);
    }

    /*
    a -> a
    a|b -> abbbb
    a||b -> aabbbb
    a|b||c -> abbccc
     */
    private static String extract(String causesOfDelay, int iteration) {
        int pipe = causesOfDelay.indexOf('|');
        if (pipe >= 0) {
            String[] split = causesOfDelay.split("\\|");
            if (iteration >= split.length) return split[split.length - 1];
            int pos = iteration;
            while (pos > 0 && split[pos].isEmpty()) pos--;
            return split[pos];
        }
        return causesOfDelay;
    }

    public void assertInitialValue(StatementAnalyserVariableVisitor.Data d, int delayedBeforeIteration, String causesOfDelay, String value) {
        Expression initialValue = d.variableInfoContainer().getPreviousOrInitial().getValue();
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(initialValue.isDelayed(), "Expected current value to be delayed in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but was " + initialValue + " for variable " + d.variableName());
            assertEquals(causesOfDelay, initialValue.causesOfDelay().toString());
        } else {
            assertTrue(initialValue.isDone(), "Expected current value to be done in iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + initialValue
                    .causesOfDelay() + " for variable " + d.variableName());
            assertEquals(value, initialValue.toString());
        }
    }

    protected final Map<String, Integer> mustSee = new HashMap<>();

    public void mustSeeIteration(CommonVisitorData cvd, int targetIteration) {
        String label = cvd.label();
        if (cvd.iteration() < targetIteration) {
            mustSee.put(label, cvd.iteration());
        } else {
            mustSee.remove(label);
        }
    }

    public Range assertRange(StatementAnalyserVisitor.Data d, String rangeExpected, String conditionExpected) {
        return assertRange(d, 1, rangeExpected, conditionExpected);
    }

    public Range assertRange(StatementAnalyserVisitor.Data d, int delayedUntil, String rangeExpected, String conditionExpected) {
        if (d.iteration() < delayedUntil) {
            CausesOfDelay causes = d.statementAnalysis().rangeData().getRange().causesOfDelay();
            assertTrue(causes.isDelayed());
            assertEquals(CauseOfDelay.Cause.VARIABLE_DOES_NOT_EXIST, causes.causesStream().findFirst().orElseThrow().cause());
            return null;
        }
        Range range = d.statementAnalysis().rangeData().getRange();
        assertEquals(rangeExpected, range.toString());
        Expression conditions = range.conditions(d.context().evaluationContext());
        assertEquals(conditionExpected, conditions.toString());
        return range;
    }

    protected void assertHc(TypeAnalyserVisitor.Data d, int delayedBefore, String s) {
        CausesOfDelay causes = d.typeAnalysis().hiddenContentDelays();
        if (d.iteration() < delayedBefore) {
            assertTrue(causes.isDelayed(),
                    "Expected hidden content to be delayed in iteration " + d.iteration() + " < " + delayedBefore);
        } else {
            assertTrue(causes.isDone(),
                    "Expected hidden content to be done in iteration " + d.iteration() + " >= " + delayedBefore);
            assertEquals(s, d.typeAnalysis().getHiddenContentTypes().toString());
        }
    }

    public record IterationInfo(int from, int toExcl, String value) {
        public static IterationInfo it0(String value) {
            return new IterationInfo(0, 1, value);
        }

        public static IterationInfo it1(String value) {
            return new IterationInfo(1, 2, value);
        }

        public static IterationInfo it(int from, int toIncl, String value) {
            return new IterationInfo(from, toIncl + 1, value);
        }

        public static IterationInfo it(int from, String value) {
            return new IterationInfo(from, Integer.MAX_VALUE, value);
        }

        public boolean accepts(int iteration) {
            return iteration >= from && iteration < toExcl;
        }
    }

    protected void assertLinked(StatementAnalyserVariableVisitor.Data d, IterationInfo... iterationInfos) {
        assertLinked(d, d.variableInfo().getLinkedVariables(), iterationInfos);
    }

    protected void assertLinked(CommonVisitorData d,
                                LinkedVariables linkedVariables,
                                IterationInfo... iterationInfos) {
        String links = linkedVariables.toString();
        for (IterationInfo ii : iterationInfos) {
            if (ii.accepts(d.iteration())) {
                assertEquals(ii.value, links, "Linked variables iteration " + d.iteration());
                return;
            }
        }
        fail("Did not see iteration info for iteration " + d.iteration());
    }

    protected void assertValue(StatementAnalyserVariableVisitor.Data d, IterationInfo... iterationInfos) {
        String value = d.currentValue().toString();
        for (IterationInfo ii : iterationInfos) {
            if (ii.accepts(d.iteration())) {
                assertEquals(ii.value, value, "Current value iteration " + d.iteration());
                return;
            }
        }
        fail("Did not see iteration info for iteration " + d.iteration());
    }
}
