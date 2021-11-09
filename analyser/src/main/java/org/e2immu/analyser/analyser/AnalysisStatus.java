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

import java.util.function.Function;

public interface AnalysisStatus {

    int pos();

    boolean isDelayed();

    boolean isProgress();

    boolean isDone();

    CausesOfDelay causesOfDelay();

    record NotDelayed(int pos) implements AnalysisStatus {
        @Override
        public boolean isDelayed() {
            return false;
        }

        @Override
        public boolean isProgress() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public CausesOfDelay causesOfDelay() {
            return CausesOfDelay.EMPTY;
        }
    }

    record Delayed(CausesOfDelay causesOfDelay, boolean progress) implements AnalysisStatus {
        public Delayed(CauseOfDelay cause) {
            this(new CausesOfDelay.SimpleSet(cause), false);
        }

        public Delayed(CausesOfDelay causes) {
            this(causes, false);
        }

        @Override
        public int pos() {
            return progress ? 0 : 1;
        }

        @Override
        public boolean isDelayed() {
            return true;
        }

        @Override
        public boolean isProgress() {
            return progress;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public CausesOfDelay causesOfDelay() {
            return causesOfDelay;
        }
    }

    AnalysisStatus DONE = new NotDelayed(2); // done this one
    AnalysisStatus RUN_AGAIN = new NotDelayed(3); // this one is run every time, unless DONE_ALL overrides (does not cause changes, nor delays)
    AnalysisStatus DONE_ALL = new NotDelayed(3); // done this one, don't do any of the others
    AnalysisStatus SKIPPED = new NotDelayed(4); // used for unreachable code, but never in the AnalyserComponents

    default AnalysisStatus combine(AnalysisStatus other) {
        if (other == null) return this;
        if (other.pos() <= 1 && pos() <= 1) {
            return new Delayed(causesOfDelay().merge(other.causesOfDelay()), pos() == 0 || other.pos() == 0);
        }
        if (other.pos() < pos()) return other;
        return this;
    }

    @FunctionalInterface
    interface AnalysisResultSupplier<S> extends Function<S, AnalysisStatus> {

    }
}
