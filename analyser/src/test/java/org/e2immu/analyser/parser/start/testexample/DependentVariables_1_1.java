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

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Nullable;

// small variant on 1

public class DependentVariables_1_1 {

    @ImmutableContainer(hc = true)
    static class XS<X> {
        private final X[] xs;

        public XS(@Independent(hc = true) X[] p, X[] source) {
            this.xs = source.clone();
            System.arraycopy(p, 0, this.xs, 0, p.length);
        }

        @Nullable
        public X getX(int index) {
            return xs[index];
        }
    }
}
