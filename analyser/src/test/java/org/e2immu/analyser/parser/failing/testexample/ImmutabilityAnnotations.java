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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.*;
import org.e2immu.annotation.eventual.BeforeMark;
import org.e2immu.annotation.eventual.Mark;
import org.e2immu.annotation.eventual.Only;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The purpose of this class is to show all immutability annotations colored in the highlighter,
 * working towards the first open source milestone.
 * <p>
 * For types OK:
 * <ul>
 *     <li>@E2Container              OK String</li>
 *     <li>@E2Immutable              OK top-most type</li>
 *     <li>@E1Container              OK subtype ManyTs</li>
 *     <li>@E1Immutable              OK subtype FreezableSet</li>
 *     <li>@MutableModifiesArguments OK subtype VariableType</li>
 *     <li>@Container                OK List, Set, ...</li>
 * </ul>
 *
 * <p>
 * For constructors OK:
 * <ul>
 *     <li>@Independent OK</li>
 *     <li>@Dependent OK</li>
 * </ul>
 * <p>
 * For methods OK:
 * <ul>
 *     <li>@Independent  OK</li>
 *     <li>@Dependent  OK</li>
 *     <li>@NotModified OK</li>
 *     <li>@Modified  OK</li>
 * </ul>
 * <p>
 * additionally on the return type (or on a field, same coloring), the dynamic type annotations
 * <ul>
 *     <li>@E2Container  OK copy</li>
 *     <li>@E1Container</li>
 *     <li>@E2Immutable </li>
 *     <li>@E1Immutable  OK generateAfter</li>
 *     <li>@BeforeMark   OK</li>
 * </ul>
 * <p>
 * For fields:
 * <ul>
 *     <li>@Variable     OK x in subtype</li>
 *     <li>@SupportData  OK strings</li>
 *     <li>@Modified     OK ts2 in ManyTs</li>
 *     <li>@NotModified</li>
 *     <li>@Final        OK</li>
 * </ul>
 * <p>
 * For parameters OK:
 * <li>
 *     <ul>@Modified  OK</ul>
 *     <ul>@NotModified  OK</ul>
 * </li>
 */


@Immutable
public class ImmutabilityAnnotations {

    @FinalFields(after = "frozen")
    static class FreezableSet {
        @Final(after = "frozen")
        private boolean frozen;

        private Set<String> strings;

        @Independent
        public FreezableSet(@NotModified List<String> list) {
            this.strings = new HashSet<>(list);
        }

        public FreezableSet(Set<String> set) {
            this.strings = set;
        }

        @NotModified // but not @Independent, because not @SupportData
        public boolean isFrozen() {
            return frozen;
        }

        @Modified
        @Mark("frozen")
        public void freeze() {
            if (frozen) throw new UnsupportedOperationException();
            frozen = true;
        }

        @Modified
        @Only(before = "frozen")
        public void addStrings(@NotModified Collection<String> input) {
            if (frozen) throw new UnsupportedOperationException();
            this.strings.addAll(input);
        }

        @Independent(absent = true)
        public Set<String> mix(@Modified Set<String> mixer) {
            mixer.addAll(strings);
            return strings;
        }

        @Independent
        @ImmutableContainer
        public Set<String> copy() {
            return Set.copyOf(strings);
        }
    }

    @BeforeMark
    @NotModified
    @NotNull
    private static FreezableSet generateBefore() {
        List<String> list = List.of("a", "b");
        return new FreezableSet(list);
    }

    @FinalFields
    @NotModified
    @NotNull
    private static FreezableSet generateAfter() {
        FreezableSet freezableSet = new FreezableSet(List.of("a", "b"));
        freezableSet.freeze();
        return freezableSet;
    }

    @NotModified
    public static void addOne(@Modified @NotNull Set<Integer> set) {
        set.add(1);
    }

    @FinalFields(absent = true)
    private static class VariableType<T> {
        @Final(absent = true)
        T t;

        @Modified
        public void setT(@NotModified @Nullable T t) {
            this.t = t;
        }

        @NotModified
        public void addT(@Modified @NotNull Set<T> set) {
            set.add(t); // WARNING! Causes null-pointer warning
        }
    }

    @FinalFields
    static class ManyTs<T> {
        @NotNull
        @NotModified
        public final T[] ts1;
        @Nullable
        @Modified
        public final T[] ts2;

        public ManyTs(@Modified @Nullable T[] ts) {
            this.ts1 = ts;
            this.ts2 = ts;
        }

        @Modified
        public void setFirst(@NotModified @Nullable T t) {
            if (ts2 != null) ts2[0] = t;
        }

        @NotModified
        @Nullable
        public T getFirst() {
            return ts1[0];
        }
    }
}
