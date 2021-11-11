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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.annotation.UtilityClass;

/**
 * Properties have numeric values, according to a number of different encoding systems.
 * We try to keep these systems as close together as possible, using constants to allow for some flexibility of refactoring.
 */

@UtilityClass
public class Level {

    private Level() {
        throw new UnsupportedOperationException();
    }

    public static final int ILLEGAL_VALUE = -2;

    public static final DV NOT_INVOLVED_DV = new DV.SingleDelay(Location.NOT_YET_SET, CauseOfDelay.Cause.NOT_INVOLVED);

    // TERNARY SYSTEM
    public static final int DELAY = -1;
    public static final int FALSE = 0;
    public static final int TRUE = 1;
    public static final DV FALSE_DV = new DV.NoDelay(0, "false");
    public static final DV TRUE_DV = new DV.NoDelay(1, "true");

    public static DV fromBoolDv(boolean b) {
        return b ? TRUE_DV : FALSE_DV;
    }
}
