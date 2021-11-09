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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.variable.Variable;

import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface CausesOfDelay extends DV {

    CausesOfDelay EMPTY = new SimpleSet(Set.of());

    static CausesOfDelay from(Set<CauseOfDelay> causes) {
        return causes.isEmpty() ? EMPTY: new SimpleSet(causes);
    }

    boolean contains(Variable variable);

    CausesOfDelay merge(CausesOfDelay other);

    Stream<CauseOfDelay> causesStream();

    record SimpleSet(java.util.Set<CauseOfDelay> causes) implements CausesOfDelay {

        public SimpleSet(CauseOfDelay cause) {
            this(Set.of(cause));
        }

        @Override
        public CausesOfDelay merge(CausesOfDelay other) {
            if (other == EMPTY) return this;
            if (this == EMPTY) return other;
            return new SimpleSet(Stream.concat(causesStream(),
                    other.causesStream()).collect(Collectors.toUnmodifiableSet()));
        }

        @Override
        public boolean contains(Variable variable) {
            return causes.stream().anyMatch(c -> variable.equals(c.variable()));
        }

        @Override
        public Stream<CauseOfDelay> causesStream() {
            return causes.stream();
        }

        @Override
        public boolean isDelayed() {
            return !causes.isEmpty();
        }

        @Override
        public boolean isDone() {
            return causes.isEmpty();
        }

        @Override
        public int value() {
            return Level.DELAY;
        }

        @Override
        public CausesOfDelay causesOfDelay() {
            return this;
        }

        @Override
        public DV min(DV other) {
            return null;
        }

        @Override
        public DV max(DV other) {
            return null;
        }

        @Override
        public DV replaceDelayBy(DV nonDelay) {
            assert nonDelay.isDone();
            return nonDelay;
        }
    }
}