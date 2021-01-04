/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.Nullable;

public class SubTypes_2 {

    private String field;

    // ERROR: toString is @Nullable, which is worse than what we demand in Object.toString()
    class NonStaticSubType {
        @Nullable
        @Override
        public String toString() {
            return field;
        }
    }


    // ERROR: toString is @Modified, which is worse than what we demand in Object.toString()
    // also warning: assigning to field outside type
    class NonStaticSubType2 {
        @Modified
        @Override
        public String toString() {
            field = "x";
            return "abc";
        }
    }

    class NonStaticSubType3 {
        @Override
        public String toString() {
            return "ok" + field;
        }
    }
}
