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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Modified;

import java.util.Set;

/**
 * A type cannot have a lower container or immutable value than any of the interfaces it implements.
 * This has to follow after computation, and will only be enforced during the 'check' phase.
 */
public class Warnings_7 {

    @Container
    interface MustBeContainer {
        void addToSet(Set<Integer> setInInterface);
    }

    @E2Immutable(recursive = true) // still, will cause an error because we had expected @ERContainer
    static class IsNotAContainer implements MustBeContainer {

        public final int i;

        IsNotAContainer(int i) {
            this.i = i;
        }

        // must cause an error, because addToSet is explicitly not modifying set (inside @Container)
        public void addToSet(@Modified Set<Integer> set) {
            set.add(i);
        }
    }
}
