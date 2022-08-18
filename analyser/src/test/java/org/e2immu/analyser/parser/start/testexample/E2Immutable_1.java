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

import org.e2immu.annotation.*;

@ImmutableContainer
public class E2Immutable_1 {

    @Nullable
    public final E2Immutable_1 parent2;
    @Final
    private int level2;
    @Nullable
    public final String value2;

    public E2Immutable_1(String value) {
        this.parent2 = null;
        level2 = 99;
        this.value2 = value;
    }

    public E2Immutable_1(@NotNull E2Immutable_1 parent2Param, String valueParam2) {
        this.parent2 = parent2Param;
        level2 = parent2Param.level2 + 2;
        this.value2 = valueParam2;
    }

    public int getLevel2() {
        return level2;
    }
}
