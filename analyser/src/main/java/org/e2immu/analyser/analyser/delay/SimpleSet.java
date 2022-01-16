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

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record SimpleSet(java.util.Set<CauseOfDelay> causes) implements CausesOfDelay {

    public SimpleSet(Location location, CauseOfDelay.Cause cause) {
        this(new SimpleCause(location, cause));
    }

    public SimpleSet(CauseOfDelay cause) {
        this(Set.of(cause));
    }

    public static CausesOfDelay from(Set<CauseOfDelay> causes) {
        return causes.isEmpty() ? EMPTY : new SimpleSet(causes);
    }

    @Override
    public String label() {
        throw new UnsupportedOperationException("No label for delays");
    }

    @Override
    public CausesOfDelay merge(CausesOfDelay other) {
        if (other.isDone()) return this;
        if (isDone()) return other;
        return new SimpleSet(Stream.concat(causesStream(), other.causesStream())
                .collect(Collectors.toUnmodifiableSet()));
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

    private DV merge(DV other) {
        return new SimpleSet(Stream.concat(causesStream(),
                other.causesOfDelay().causesStream()).collect(Collectors.toUnmodifiableSet()));
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
}
