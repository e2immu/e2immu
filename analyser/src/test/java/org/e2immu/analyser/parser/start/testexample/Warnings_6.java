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

import java.util.List;
import java.util.Set;

/**
 * A type can have a lower container or immutable value than any of the interfaces it implements,
 * as long as this value follows without conflict on the methods defined.
 */
public class Warnings_6 {

    @Container
    interface MustBeContainer {

        void print(List<String> strings);
    }

    @E2Immutable(recursive = true) // still, will cause an error because we had expected @ERContainer
    static class IsNotAContainer implements MustBeContainer {

        public final int i;

        // existing method follows the @Container rules
        @Override
        public void print(List<String> strings) {
            System.out.println(i + strings.size());
        }

        IsNotAContainer(int i) {
            this.i = i;
        }

        // new method (not in MustBeContainer) does not follow the rules
        public void addToSet(@Modified Set<Integer> set) {
            set.add(i);
        }
    }
}
