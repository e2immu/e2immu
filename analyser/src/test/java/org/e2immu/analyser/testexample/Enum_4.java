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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;

@E2Container
public enum Enum_4 {
    ONE(1), TWO(2), THREE(3);

    public final int cnt;

    Enum_4(int cnt) {
        this.cnt = cnt;
    }

    public int getCnt() {
        return cnt;
    }

    @Constant("THREE")
    public static Enum_4 highest() {
        assert 1 == ONE.getCnt(); // warning: always true
        assert 2 == TWO.cnt;      // warning: always true
        return THREE;
    }
}
