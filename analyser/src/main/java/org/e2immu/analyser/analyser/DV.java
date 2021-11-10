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
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
Delayable Value
 */
public interface DV {

    DV MAX_INT_DV = new NoDelay(Integer.MAX_VALUE);

    DV MIN_INT_DV = new NoDelay(Integer.MIN_VALUE);

    int value();

    CausesOfDelay causesOfDelay();

    boolean isDelayed();

    boolean isDone();

    DV min(DV other);

    DV max(DV other);

    DV maxIgnoreDelay(DV other);

    DV replaceDelayBy(DV nonDelay);

    default boolean valueIsTrue() {
        return value() == Level.TRUE;
    }

    default boolean valueIsFalse() {
        return value() == Level.FALSE;
    }

    record NoDelay(int value) implements DV {
        public NoDelay {
            assert value >= 0;
        }

        @Override
        public CausesOfDelay causesOfDelay() {
            return CausesOfDelay.EMPTY;
        }

        @Override
        public DV min(DV other) {
            if (other.value() > value) return this;
            // if other is a delay, its value is less than ours!
            return other;
        }

        @Override
        public DV max(DV other) {
            if (other.value() >= value || other.isDelayed()) return other;
            return this;
        }

        @Override
        public DV maxIgnoreDelay(DV other) {
            if (other.value() >= value) return other;
            return this;
        }

        @Override
        public DV replaceDelayBy(DV nonDelay) {
            assert nonDelay.isDone();
            return this;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public boolean isDelayed() {
            return false;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    record SingleDelay(CauseOfDelay causeOfDelay, int value) implements DV, AnalysisStatus {

        public SingleDelay(CauseOfDelay causeOfDelay) {
            this(causeOfDelay, Level.DELAY);
        }

        public SingleDelay(WithInspectionAndAnalysis withInspectionAndAnalysis, CauseOfDelay.Cause cause) {
            this(new Location(withInspectionAndAnalysis), cause);
        }

        public SingleDelay(Location location, CauseOfDelay.Cause cause) {
            this(new CauseOfDelay.SimpleCause(location, cause), Level.DELAY);
        }

        @Override
        public CausesOfDelay causesOfDelay() {
            return new CausesOfDelay.SimpleSet(causeOfDelay);
        }

        private DV merge(DV other) {
            if (other instanceof SingleDelay sd && sd.causeOfDelay.equals(causeOfDelay)) return this;
            return new CausesOfDelay.SimpleSet(Stream.concat(Stream.of(causeOfDelay),
                    other.causesOfDelay().causesStream()).collect(Collectors.toUnmodifiableSet()));
        }

        @Override
        public DV min(DV other) {
            if (other.isDelayed()) {
                return merge(other);
            }
            return this; // other is not a delay
        }

        @Override
        public DV max(DV other) {
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
        public boolean isDone() {
            return false;
        }

        @Override
        public int pos() {
            return 1;
        }

        @Override
        public boolean isDelayed() {
            return true;
        }

        @Override
        public boolean isProgress() {
            return false;
        }

        @Override
        public DV replaceDelayBy(DV nonDelay) {
            assert nonDelay.isDone();
            return nonDelay;
        }

        @Override
        public String toString() {
            return "SingleDelay{" +
                    "causeOfDelay=" + causeOfDelay +
                    ", value=" + value +
                    '}';
        }
    }
}
