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

import org.e2immu.annotation.E1Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/*
while this one tests modification (in inner class, has effect on outer class)
it also relies on Inner not being E2Immutable when Modification_13 is only E1Immutable
 */

@E1Container
public class Modification_13 {

    @Modified
    private final Set<String> set;

    public Modification_13(Collection<String> input) {
        set = new HashSet<>(input);
    }

    @NotModified
    public Set<String> getSet() {
        return set;
    }

    @E1Container
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
