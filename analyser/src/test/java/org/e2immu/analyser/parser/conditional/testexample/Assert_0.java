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

package org.e2immu.analyser.parser.conditional.testexample;

public class Assert_0 {

    interface AnalysisStatus {
        boolean isDelayed();

        CausesOfDelay causesOfDelay();

        int numberOfDelays();

        boolean isProgress();
    }

    interface CausesOfDelay extends AnalysisStatus {
        int LIMIT = 10;

        CausesOfDelay addProgress(boolean progress);
    }

    record NotDelayed() implements AnalysisStatus {
        @Override
        public boolean isDelayed() {
            return false;
        }

        @Override
        public CausesOfDelay causesOfDelay() {
            return SimpleSet.EMPTY;
        }

        @Override
        public int numberOfDelays() {
            return 0;
        }

        @Override
        public boolean isProgress() {
            return false;
        }
    }

    static class SimpleSet implements CausesOfDelay {
        static SimpleSet EMPTY = new SimpleSet();

        public AnalysisStatus combine(AnalysisStatus other, boolean limit) {
            if (other instanceof NotDelayed) return this;
            assert other.isDelayed();
            assert isDelayed();
            CausesOfDelay merge;
            if (limit && (other.numberOfDelays() > LIMIT || numberOfDelays() > LIMIT)) {
                merge = this;
            } else {
                merge = merge(other.causesOfDelay());
            }
            return merge.addProgress(other.isProgress());
        }

        public CausesOfDelay merge(CausesOfDelay other) {
            return this;
        }

        @Override
        public boolean isDelayed() {
            return true;
        }

        @Override
        public CausesOfDelay causesOfDelay() {
            return null;
        }

        @Override
        public int numberOfDelays() {
            return 0;
        }

        @Override
        public boolean isProgress() {
            return false;
        }

        @Override
        public CausesOfDelay addProgress(boolean progress) {
            return null;
        }
    }
}
