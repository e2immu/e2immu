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

public enum AnalysisStatus {
    PROGRESS(0), // changes, but not yet fully done
    DELAYS(1), // no changes, due to delays
    DONE(2), // done this one

    RUN_AGAIN(3), // this one is run every time, unless DONE_ALL overrides (does not cause changes, nor delays)
    DONE_ALL(3), // done this one, don't do any of the others

    SKIPPED(4) // used for unreachable code, but never in the AnalyserComponents
    ;

    private final int pos;

    AnalysisStatus(int pos) {
        this.pos = pos;
    }

    public AnalysisStatus combine(AnalysisStatus other) {
        if (other == null) return this;
        if (other.pos < pos) return other;
        return this;
    }


    @FunctionalInterface
    interface AnalysisResultSupplier<S> extends Function<S, AnalysisStatus> {

    }
}
