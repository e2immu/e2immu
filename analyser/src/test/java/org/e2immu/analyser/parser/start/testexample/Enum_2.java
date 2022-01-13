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

public enum Enum_2 {
    ONE(1, "un"), TWO(2, "zwei"), THREE(3);

    public static final Enum_2 MAX = THREE;

    public final int cnt;
    private final String msg;

    Enum_2(int cnt) {
        this(cnt, "=" + cnt);
    }

    Enum_2(int cnt, String msg) {
        this.cnt = cnt;
        this.msg = msg;
    }

    @Override
    public String toString() {
        return msg;
    }
}
