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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
the set is immutable, but its contents is mutable, and exposed.
 */
public class Independent_2 {

    @Container
    @Independent
    static class I {
        private int i;

        public I(int i) {
            this.i = i;
        }

        public void setI(int i) {
            this.i = i;
        }

        public int getI() {
            return i;
        }
    }

    @Container
    static class ISet {
        private final Set<I> set;

        private ISet() {
            set = Set.of();
        }

        public ISet(@Independent List<Integer> is) {
            set = is.stream().map(I::new).collect(Collectors.toUnmodifiableSet());
        }

        @Container // dependent, as "I" is mutable
        public Stream<I> stream() {
            return set.stream();
        }

        @Container // dependent, as "I" is mutable
        public static ISet of(int i) {
            return new ISet(List.of(i));
        }

        /*
         IMPROVE: at the moment, we cannot know that by using this constructor, we end up with an
         empty set, which, instead of being mutable, renders it immutable
         */
        @ImmutableContainer(contract = true)
        public static ISet of() {
            return new ISet();
        }
    }
}
