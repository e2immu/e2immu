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
import org.e2immu.analyser.analyser.Properties;

// used for situations where progress can be true while causes is done.
public record ProgressAndDelay(boolean progress, CausesOfDelay causes) {
    public static final ProgressAndDelay EMPTY = new ProgressAndDelay(false, CausesOfDelay.EMPTY);

    public ProgressAndDelay combine(ProgressAndDelay other) {
        return new ProgressAndDelay(progress || other.progress, causes.merge(other.causes));
    }

    public ProgressAndDelay combine(AnalysisStatus other) {
        return new ProgressAndDelay(progress || other.isProgress(), causes.merge(other.causesOfDelay()));
    }

    public ProgressAndDelay addProgress(boolean progress) {
        if(progress) {
            return new ProgressAndDelay(true, causes);
        }
        return this;
    }

    public ProgressAndDelay merge(CausesOfDelay anyDelays) {
        if(anyDelays.isDelayed()) {
            return new ProgressAndDelay(progress, causes.merge(anyDelays));
        }
        return this;
    }

    public boolean isDelayed() {
        return causes.isDelayed();
    }

    public AnalysisStatus toAnalysisStatus() {
        return AnalysisStatus.of(causes).addProgress(progress);
    }

    public boolean isDone() {
        return causes().isDone();
    }
}
