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
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.WeightedGraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimpleSet implements CausesOfDelay {
    // DO NOT USE, use CausesOfDelay.EMPTY!
    public static final SimpleSet EMPTY = new SimpleSet(Set.of());

    private final Set<CauseOfDelay> causes;

    // only to be used for CausesOfDelay.EMPTY

    private SimpleSet(Set<CauseOfDelay> causes) {
        this.causes = causes;
    }

    public SimpleSet(Location location, CauseOfDelay.Cause cause) {
        this(new SimpleCause(location, cause));
    }

    public SimpleSet(CauseOfDelay cause) {
        this(Set.of(cause));
    }

    public static CausesOfDelay from(Set<CauseOfDelay> causes) {
        return causes.isEmpty() ? CausesOfDelay.EMPTY : mergeIntoMapAndReturn(causes.stream(), new HashMap<>());
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

    @Override
    public String label() {
        throw new UnsupportedOperationException("No label for delays");
    }

    /*
    A single merge will be slower, but we'll have fewer delays to merge in complex methods, which makes it faster again...
     */
    @Override
    public CausesOfDelay merge(CausesOfDelay other) {
        if (other.isDone()) return this;
        if (isDone()) return other;
        // more complicated than simply merge two sets. We keep only the earliest location of each delay
        Map<String, CauseOfDelay> map = new HashMap<>();
        causes.forEach(c -> map.merge(c.withoutStatementIdentifier(), c, (c1, c2) -> {
            throw new UnsupportedOperationException("This set should already have been merged properly: " + causes);
        }));
        return mergeIntoMapAndReturn(other.causesStream(), map);
    }

    private static SimpleSet mergeIntoMapAndReturn(Stream<CauseOfDelay> causes, Map<String, CauseOfDelay> map) {
        causes.forEach(c -> map.merge(c.withoutStatementIdentifier(), c, (c1, c2) -> {
            String i1 = c1.location().statementIdentifierOrNull();
            String i2 = c2.location().statementIdentifierOrNull();
            if (i1 == null && i2 == null) return c1;
            if (i1 == null) return c2;
            if (i2 == null) return c1;
            return i1.compareTo(i2) <= 0 ? c1 : c2;
        }));
        return new SimpleSet(Set.copyOf(map.values()));
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
        return set.isEmpty() ? CausesOfDelay.EMPTY : new SimpleSet(set);
    }

    @Override
    public int pos() {
        return 1;
    }

    @Override
    public boolean isDelayed() {
        return !causes.isEmpty();
    }

    @Override
    public boolean isProgress() {
        return false;
    }

    @Override
    public boolean isDone() {
        return causes.isEmpty();
    }

    @Override
    public int value() {
        return -1;
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return this;
    }

    @Override
    public AnalysisStatus addProgress(boolean progress) {
        if (progress) {
            return new ProgressWrapper(this);
        }
        return this;
    }

    @Override
    public DV min(DV other) {
        if (this == MIN_INT_DV) return other;
        if (other == MIN_INT_DV) return this;
        if (other.isDelayed()) {
            return merge(other);
        }
        // other is not delayed
        return this;
    }

    @Override
    public DV minIgnoreNotInvolved(DV other) {
        if (this == MIN_INT_DV) return other;
        if (other == MIN_INT_DV) return this;
        if (other.isDelayed()) {
            return merge(other);
        }
        // other is not delayed
        return this;
    }

    private DV merge(DV other) {
        Map<String, CauseOfDelay> map = new HashMap<>();
        causes.forEach(c -> map.merge(c.withoutStatementIdentifier(), c, (c1, c2) -> {
            throw new UnsupportedOperationException("This set should already have been merged properly: " + causes);
        }));
        return mergeIntoMapAndReturn(other.causesOfDelay().causesStream(), map);
    }

    @Override
    public DV max(DV other) {
        if (this == MIN_INT_DV) return other;
        if (other == MIN_INT_DV) return this;
        if (other.isDelayed()) {
            return merge(other);
        }
        return this; // other is not a delay
    }

    @Override
    public DV maxIgnoreDelay(DV other) {
        if (other.isDelayed()) {
            return merge(other);
        }
        return other; // other is not a delay
    }

    @Override
    public DV replaceDelayBy(DV nonDelay) {
        assert nonDelay.isDone();
        return nonDelay;
    }

    @Override
    public int compareTo(WeightedGraph.Weight o) {
        return value() - ((DV) o).value();
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
    public AnalysisStatus combine(AnalysisStatus other) {
        if (other instanceof NotDelayed) return this;
        assert other.isDelayed();
        assert isDelayed();
        return merge(other.causesOfDelay()).addProgress(other.isProgress());
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
}
