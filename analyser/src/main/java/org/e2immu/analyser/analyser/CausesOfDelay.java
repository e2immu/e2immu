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

import org.e2immu.analyser.analyser.delay.AbstractDelay;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.annotation.NotNull;

import java.util.Set;
import java.util.stream.Stream;

public interface CausesOfDelay extends DV, AnalysisStatus {

    boolean contains(Variable variable);

    @NotNull
    CausesOfDelay merge(CausesOfDelay other);

    @NotNull
    Stream<CauseOfDelay> causesStream();

    CausesOfDelay removeAll(Set<CauseOfDelay> breaks);

    int maxPriority();

    CausesOfDelay EMPTY = new AbstractDelay() {

        @Override
        public AnalysisStatus combine(AnalysisStatus other) {
            return other;
        }

        @Override
        public AnalysisStatus combine(AnalysisStatus other, boolean limit) {
            return other;
        }

        @Override
        public int numberOfDelays() {
            return 0;
        }

        @Override
        public boolean contains(Variable variable) {
            return false;
        }

        @Override
        public CausesOfDelay merge(CausesOfDelay other) {
            return other;
        }

        @Override
        public Stream<CauseOfDelay> causesStream() {
            return Stream.of();
        }

        @Override
        public CausesOfDelay removeAll(Set<CauseOfDelay> breaks) {
            return this;
        }

        @Override
        public boolean isDelayed() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public DV min(DV other) {
            if (other == MIN_INT_DV) return this;
            return other;
        }

        @Override
        public DV max(DV other) {
            if (other == MIN_INT_DV) return this;
            return other;
        }

        @Override
        public DV maxIgnoreDelay(DV other) {
            return other;
        }

        @Override
        public DV replaceDelayBy(DV nonDelay) {
            return this;
        }

        @Override
        public DV minIgnoreNotInvolved(DV other) {
            if (other == MIN_INT_DV) return this;
            return other;
        }

        @Override
        public String toString() {
            return "";
        }
    };
}