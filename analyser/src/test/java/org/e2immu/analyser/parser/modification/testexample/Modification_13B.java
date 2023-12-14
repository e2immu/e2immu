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
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/*
while this one tests modification (in inner class, has effect on outer class)
it also relies on Inner not being immutable when Modification_13 is only @FinalFields
 */

@FinalFields
@Container
public class Modification_13B<T> {

    @Modified
    private final Set<T> set;

    public Modification_13B(Collection<T> input) {
        set = new HashSet<>(input);
    }

    @NotModified
    public Set<T> getSet() {
        return set;
    }

    @FinalFields
    @Container
    class Inner {

        private final int threshold;

        public Inner(int threshold) {
            this.threshold = threshold;
        }

        @Modified
        public void clearIfExceeds(int i) {
            if (i > threshold) {
                set.clear();
            }
        }
    }
}
