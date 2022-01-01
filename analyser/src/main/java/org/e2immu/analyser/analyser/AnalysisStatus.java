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

import org.e2immu.analyser.analyser.delay.NotDelayed;

import java.util.function.Function;

public interface AnalysisStatus {

    static AnalysisStatus of(CausesOfDelay merge) {
        return merge.isDelayed() ? merge : DONE;
    }

    static AnalysisStatus of(DV merge) {
        return merge.isDelayed() ? merge.causesOfDelay() : DONE;
    }

    int pos();

    boolean isDelayed();

    boolean isProgress();

    boolean isDone();

    CausesOfDelay causesOfDelay();

    AnalysisStatus addProgress(boolean progress);

    AnalysisStatus combine(AnalysisStatus other);

    // delayed = 1; progress = 0
    AnalysisStatus DONE = new NotDelayed(2, "DONE"); // done this one
    AnalysisStatus RUN_AGAIN = new NotDelayed(3, "RUN_AGAIN"); // this one is run every time, unless DONE_ALL overrides (does not cause changes, nor delays)
    AnalysisStatus DONE_ALL = new NotDelayed(3, "DONE_ALL"); // done this one, don't do any of the others
    AnalysisStatus NOT_YET_EXECUTED = new NotDelayed(4, "NOT_YET_EXECUTED"); // initial value, always removed upon combining

    @FunctionalInterface
    interface AnalysisResultSupplier<S> extends Function<S, AnalysisStatus> {

    }

}
