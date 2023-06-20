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

package org.e2immu.analyser.parser.tool.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.Modified;

import java.util.Random;

/*
Goal of the test is to detect which "external" methods, fields and types are being used.
The can then be put in Annotated API files.
 */
@FinalFields @Container
public class CollectUsage_0 {

    private final Random random;

    public CollectUsage_0(int seed) {
        this.random = new Random(seed);
        System.out.println("Seed: " + seed);
    }

    @Modified
    public int next() {
        return random.nextInt();
    }
}
