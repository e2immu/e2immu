/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.testexample;

import com.google.common.collect.ImmutableSet;
import org.e2immu.annotation.*;

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


@E2Immutable
public class ImmutabilityAnnotations {

    @E1Immutable(after = "frozen")
    static class FreezableSet {
        @Final(after = "frozen")
        private boolean frozen;

        @SupportData // implying @NotModified type @E1Immutable (which it will be), but there's a dependent constructor
        private Set<String> strings;

        @Independent
        public FreezableSet(@NotModified List<String> list) {
            this.strings = new HashSet<>(list);
        }

        @Dependent
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

        @Dependent
        public Set<String> mix(@Modified Set<String> mixer) {
            mixer.addAll(strings);
            return strings;
        }

        @Independent
        @E2Container
        public Set<String> copy() {
            return ImmutableSet.copyOf(strings);
        }
    }

    @BeforeMark
    @NotModified
    @NotNull
    private static FreezableSet generateBefore() {
        List<String> list = List.of("a", "b");
        return new FreezableSet(list);
    }

    @E1Immutable
    @NotModified
    @Precondition("not (this.frozen)")
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

    @MutableModifiesArguments
    private static class VariableType<T> {
        @Variable
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

    @E1Immutable
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
