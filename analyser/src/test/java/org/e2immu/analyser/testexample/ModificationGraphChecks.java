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

import org.e2immu.annotation.*;

@E2Container
public class ModificationGraphChecks {

    @MutableModifiesArguments
    static class C1 {

        @Variable
        private int i;

        @Modified
        public int incrementAndGet() {
            return ++i;
        }

        @Modified // <1>
        public int useC2(@Modified C2 c2) {
            return i + c2.incrementAndGetWithI();
        }

    }

    @E1Immutable
    static class C2 {

        private final int j;

        @Modified
        private final C1 c1;

        public C2(int j, @Modified C1 c1) {
            this.c1 = c1;
            this.j = j;
        }

        @Modified
        public int incrementAndGetWithI() {
            return c1.incrementAndGet() + j;
        }
    }
}
