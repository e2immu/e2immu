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

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.HashSet;
import java.util.Set;

public class ModifiedThis {

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
