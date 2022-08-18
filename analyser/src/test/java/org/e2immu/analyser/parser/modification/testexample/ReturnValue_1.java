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

package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Final;
import org.e2immu.annotation.Modified;
import org.junit.jupiter.api.Test;

/*
more complex; no value expected for nextInt.
 */
public class ReturnValue_1 {

    @Container
    static class Random {
        @Final(absent = true)
        private int seed;

        public Random(int seed) {
            this.seed = seed;
        }

        @Modified
        public int next() {
            seed = (23 * seed + 41) % 149;
            return seed;
        }
    }

    private final Random random = new Random(432);

    @Modified
    public int nextInt(int max) {
        return random.next() % max;
    }

    @Test
    public void test() {
        for (int i = 0; i < 20; i++) {
            System.out.println(nextInt(50));
        }
    }
}
