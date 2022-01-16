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
import org.e2immu.analyser.analyser.CausesOfDelay;

public record NotDelayed(int pos, String name) implements AnalysisStatus {
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

    @Override
    public AnalysisStatus addProgress(boolean progress) {
        return this;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public AnalysisStatus combine(AnalysisStatus other) {
        if (other instanceof NotDelayed) return pos < other.pos() ? this : other;
        assert other.isDelayed();
        return other;
    }

    @Override
    public AnalysisStatus combineAndLimit(AnalysisStatus other) {
        return combine(other);
    }

    @Override
    public int numberOfDelays() {
        return 0;
    }
}
