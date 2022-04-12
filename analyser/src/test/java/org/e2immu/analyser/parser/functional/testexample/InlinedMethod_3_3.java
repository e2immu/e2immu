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
import org.e2immu.annotation.Modified;

import java.util.Random;

// if you want to see the modifications made by nextInt(), make a field
public class InlinedMethod_3_3 {

    private final Random random = new Random();

    @Modified
    public int plusRandom(int i) {
        int r = random.nextInt();
        return i + r;
    }

    @Constant(absent = true)
    public int difference31() {
        return plusRandom(3) - plusRandom(1);
    }

    @Constant(absent = true)
    public int difference11() {
        return plusRandom(1) - plusRandom(1);
    }
}
