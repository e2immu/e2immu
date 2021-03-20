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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@E2Immutable
public class ObjectFlowFreezableSet {

    @E2Container(after = "mark")
    static class FreezableSet {
        private final Set<String> set = new HashSet<>();
        private boolean frozen;

        @Only(after = "mark")
        @NotModified
        @NotNull1
        public Stream<String> stream() {
            if (!frozen) throw new UnsupportedOperationException();
            return set.stream();
        }

        @Only(before = "mark")
        @Modified
        public void add(String s) {
            if (frozen) throw new UnsupportedOperationException();
            set.add(s);
        }

        @Mark("mark")
        @Modified
        public void freeze() {
            if (frozen) throw new UnsupportedOperationException();
            frozen = true;
        }

        @NotModified
        public boolean isFrozen() {
            return frozen;
        }
    }

    static int method1() {
        FreezableSet set1 = new FreezableSet();
        set1.add("abc");
        set1.add("def");
        if (set1.isFrozen()) throw new UnsupportedOperationException();
        set1.freeze();
        if (!set1.isFrozen()) throw new UnsupportedOperationException();
        return (int) set1.stream().count();
    }

    static int method2() {
        FreezableSet set2 = new FreezableSet();
        set2.add("abc");
        set2.add("def");
        return (int) set2.stream().count(); // should throw ERROR!!
    }

    static int method3() {
        FreezableSet set3 = new FreezableSet();
        set3.add("abc");
        set3.freeze();
        set3.add("def");  // should throw ERROR!!
        return (int) set3.stream().count();
    }

    @E2Container
    @NotModified
    static FreezableSet method4() {
        FreezableSet set4 = new FreezableSet();
        set4.add("abc");
        set4.add("def");
        if (set4.isFrozen()) throw new UnsupportedOperationException();
        set4.freeze();
        return set4;
    }

    @E2Container
    @NotNull
    static final FreezableSet SET5 = method4();

    // not frozen yet
    @E2Container(absent = true)
    @BeforeMark
    @NotModified
    static FreezableSet method6() {
        FreezableSet set6 = new FreezableSet();
        set6.add("abc");
        set6.add("def");
        return set6;
    }

    private static boolean method7$Precondition(FreezableSet set7) { return !set7.frozen; }
    // result frozen
    @E2Container
    @Identity
    @NotModified
    @NotNull
    static FreezableSet method7(@Modified @NotNull FreezableSet set7) {
        if (set7.isFrozen()) throw new UnsupportedOperationException();
        set7.freeze();
        return set7;
    }

    @E2Container
    @NotNull
    static final FreezableSet SET8 = method7(method6());

    static void method9() {
        System.out.println("Have "+SET8.stream().count());
        SET8.add("xx"); // should throw ERROR!!
    }

    @E2Container(absent = true)
    @NotNull
    static final FreezableSet SET10 = method6();

}
