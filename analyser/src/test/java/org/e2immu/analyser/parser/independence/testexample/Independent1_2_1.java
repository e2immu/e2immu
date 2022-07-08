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

package org.e2immu.analyser.parser.independence.testexample;

import org.e2immu.annotation.ERContainer;
import org.e2immu.annotation.Independent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

/*
variant, using Strings... should NOT be Independent1, but Independent
because String is @ERContainer
 */
@ERContainer
public class Independent1_2_1 {
    private final String[] ts;

    /*
    we expect @Independent1 rather than @Independent, as the content
    of 'ts' will be linked copied into the content of 'set'.
     */
    public Independent1_2_1(@Independent IntFunction<String> generator) {
        this.ts = new String[4];
        Arrays.setAll(this.ts, generator);
    }

    @Independent
    public Set<String> getSet() {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, this.ts);
        return set;
    }
}
