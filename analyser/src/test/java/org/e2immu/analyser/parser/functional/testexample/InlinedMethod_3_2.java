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

package org.e2immu.analyser.parser.functional.testexample;

import org.e2immu.annotation.Constant;
import org.e2immu.annotation.NotModified;

import java.util.Random;

// without contract, plusRandom is not modifying
public class InlinedMethod_3_2 {

    @NotModified
    public static int plusRandom(int i) {
        int r = new Random().nextInt();
        return i + r;
    }

    @Constant("2")
    public static int difference31() {
        return plusRandom(3) - plusRandom(1);
    }

    @Constant("0")
    public static int difference11() {
        return plusRandom(1) - plusRandom(1);
    }
}
