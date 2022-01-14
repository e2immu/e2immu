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

package org.e2immu.analyser.parser.start.testexample;


public enum Enum_1 {
    ONE(1), TWO(2), THREE(3);

    public final int cnt;

    Enum_1(int cnt) {
        this.cnt = cnt;
    }

    public int best(Enum_1 other) {
        return Math.max(cnt, other.cnt);
    }

    public int posInList() {
        for (int i = 0; i < values().length; i++) {
            if (values()[i] == this) return i;
        }
        throw new UnsupportedOperationException();
    }
}
