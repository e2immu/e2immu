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

package org.e2immu.analyser.analyser.delay;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class SimpleSet extends AbstractDelay {
    private final List<CauseOfDelay> causes;
    private final int maxPriority;

    // only to be used for CausesOfDelay.EMPTY

    SimpleSet(List<CauseOfDelay> causes, int maxPriority) {
        this.causes = causes;
        this.maxPriority = maxPriority;
        assert causes.size() > 1;
        assert maxPriority != CauseOfDelay.LOW;
    }

    @Override
    public int maxPriority() {
        return maxPriority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleSet simpleSet = (SimpleSet) o;
        return causes.equals(simpleSet.causes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(causes);
    }

    /*
    A single merge will be slower, but we'll have fewer delays to merge in complex methods, which makes it faster again...
     */
    @Override
    public CausesOfDelay merge(CausesOfDelay other) {
        if (other.isDone()) return this;
        if (maxPriority < other.maxPriority()) return other;
        if (maxPriority > other.maxPriority()) return this;

        // more complicated than simply merge two sets. We keep only the earliest location of each delay
        Map<String, CauseOfDelay> map = new HashMap<>();
        causes.forEach(c -> map.merge(c.withoutStatementIdentifier(), c, (c1, c2) -> {
            throw new UnsupportedOperationException("This set should already have been merged properly: " + causes);
        }));
        return mergeIntoMapAndReturn(other.causesStream(), map, maxPriority);
    }

    public static CausesOfDelay mergeIntoMapAndReturn(Stream<CauseOfDelay> causes, Map<String, CauseOfDelay> map, int maxPriority) {
        // all priorities are equal
        causes.forEach(c -> map.merge(c.withoutStatementIdentifier(), c, (c1, c2) -> {
            String i1 = c1.location().statementIdentifierOrNull();
            String i2 = c2.location().statementIdentifierOrNull();
            if (i1 == null && i2 == null) return c1;
            if (i1 == null) return c2;
            if (i2 == null) return c1;
            return i1.compareTo(i2) <= 0 ? c1 : c2;
        }));
        if (map.size() == 1) {
            return new SingleDelay(map.values().stream().findFirst().orElseThrow());
        }
        return new SimpleSet(map.values().stream().toList(), maxPriority);
    }

    @Override
    public boolean contains(Variable variable) {
        return causes.stream()
                .filter(c -> c instanceof VariableCause)
                .anyMatch(c -> variable.equals(((VariableCause) c).variable()));
    }

    @Override
    public Stream<CauseOfDelay> causesStream() {
        return causes.stream();
    }

    @Override
    public CausesOfDelay removeAll(Set<CauseOfDelay> breaks) {
        Set<CauseOfDelay> set = causes.stream().filter(c -> !breaks.contains(c)).collect(Collectors.toUnmodifiableSet());
        return DelayFactory.createDelay(set);
    }

    @Override
    public String toString() {
        if (causes.size() > 10) {
            return "[" + causes.size() + " delays]";
        }
        return causes.stream().map(CauseOfDelay::toString)
                .sorted()
                .collect(Collectors.joining(";"));
    }

    @Override
    public AnalysisStatus combine(AnalysisStatus other, boolean limit) {
        if (other instanceof NotDelayed) return this;
        assert other.isDelayed();
        assert isDelayed();
        CausesOfDelay merge;
        if (limit && (other.numberOfDelays() > LIMIT || numberOfDelays() > LIMIT)) {
            merge = this;
        } else {
            merge = merge(other.causesOfDelay());
        }
        return merge.addProgress(other.isProgress());
    }

    @Override
    public int numberOfDelays() {
        return causes.size();
    }

    @Override
    public CausesOfDelay translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        return DelayFactory.createDelay(causesStream().map(c -> c.translate(inspectionProvider, translationMap)).collect(Collectors.toUnmodifiableSet()));
    }
}
