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

public record ProgressWrapper(CausesOfDelay causesOfDelay) implements AnalysisStatus {

    public ProgressWrapper {
        assert causesOfDelay.isDelayed();
    }

    @Override
    public int pos() {
        return 0;
    }

    @Override
    public boolean isDelayed() {
        return true;
    }

    @Override
    public boolean isProgress() {
        return true;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public AnalysisStatus addProgress(boolean progress) {
        return this;
    }

    @Override
    public AnalysisStatus combine(AnalysisStatus other) {
        if (other instanceof NotDelayed) return this;
        assert other.isDelayed();
        return new ProgressWrapper(causesOfDelay.merge(other.causesOfDelay()));
    }
}