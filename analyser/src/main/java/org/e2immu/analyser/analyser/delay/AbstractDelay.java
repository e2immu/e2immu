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


public abstract class AbstractDelay implements CausesOfDelay {

    @Override
    public int maxPriority() {
        return CauseOfDelay.LOW;
    }

    @Override
    public int pos() {
        return 1;
    }

    @Override
    public boolean isProgress() {
        return false;
    }

    @Override
    public AnalysisStatus addProgress(boolean progress) {
        if (progress) {
            return new ProgressWrapper(this);
        }
        return this;
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
    public boolean isDelayed() {
        return true;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public String label() {
        return toString();
    }

    @Override
    public int compareTo(DV o) {
        return value() - o.value();
    }


    @Override
    public DV min(DV other) {
        if (this.isInitialDelay()) return other;
        if (other.isInitialDelay()) return this;
        if (other.isDelayed()) {
            return merge(other.causesOfDelay());
        }
        // other is not delayed
        return this;
    }

    @Override
    public DV minIgnoreNotInvolved(DV other) {
        if (this.isInitialDelay()) return other;
        if (other.isInitialDelay()) return this;
        if (other.isDelayed()) {
            return merge(other.causesOfDelay());
        }
        // other is not delayed
        return this;
    }

    @Override
    public DV max(DV other) {
        if (this.isInitialDelay()) return other;
        if (other.isInitialDelay()) return this;
        if (other.isDelayed()) {
            return merge(other.causesOfDelay());
        }
        return this; // other is not a delay
    }

    @Override
    public DV maxIgnoreDelay(DV other) {
        if (other.isDelayed()) {
            return merge(other.causesOfDelay());
        }
        return other; // other is not a delay
    }

    @Override
    public AnalysisStatus combine(AnalysisStatus other) {
        if (other instanceof NotDelayed) return this;
        assert other.isDelayed();
        return merge(other.causesOfDelay()).addProgress(other.isProgress());
    }

    @Override
    public AnalysisStatus combine(AnalysisStatus other, boolean limit) {
        return combine(other);
    }

    @Override
    public DV replaceDelayBy(DV nonDelay) {
        assert nonDelay.isDone();
        return nonDelay;
    }
}
