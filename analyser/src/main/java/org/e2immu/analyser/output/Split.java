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

package org.e2immu.analyser.output;

public enum Split {
    NEVER(0),
    EASY_L(1),
    EASY_R(2),
    EASY(3),
    BEGIN_END(4), // if you split at {, then also at }
    ALWAYS(5);

    private final int rank;

    Split(int rank) {
        this.rank = rank;
    }

    public Split easiest(Split split) {
        return split.rank > this.rank ? split : this;
    }
}
