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

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.HashSet;
import java.util.Set;

public class Modification_12 {

    static class ParentClass {
        @Modified
        private final Set<String> set = new HashSet<>();

        @Modified
        public void clear() {
            set.clear();
        }

        @Modified
        public void clearAndLog() {
            clear();
            System.out.println("Cleared!");
        }

        @Modified
        public void clearAndLog2() {
            this.clear();
            System.out.println("Cleared2!");
        }

        @Modified
        public void add(String s) {
            set.add(s);
        }

        @NotModified
        public Set<String> getSet() {
            return set;
        }
    }

    static class ChildClass extends ParentClass {

        private final Set<String> childSet = new HashSet<>();

        @Modified
        public void clearAndLog() {
            super.clearAndLog();
            System.out.println("Cleared from child");
        }

        @Modified
        public void addToChild(String s) {
            childSet.add(s);
        }

        @Modified
        public void clearAndAdd(String s) {
            super.clearAndLog();
            this.childSet.add(s);
        }

        @NotModified
        public Set<String> getChildSet() {
            return childSet;
        }

        class InnerOfChild {

            @Modified
            public void clear() {
                ChildClass.super.clear();
            }
        }
    }


}
