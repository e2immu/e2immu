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

/*
 method-local class; the analyser recognises them, but that's just about it.
 I do not really see their reason for existence.
*/

public class SubTypes_1 {

    protected static String methodWithSubType() {
        String s = "abc";

        class KV {
            final String key;
            final String value;

            KV(String key, String value) {
                this.key = key;
                this.value = value + s;
            }

            @Override
            public String toString() {
                return "KV=(" + key + "," + value + ")";
            }
        }

        KV kv1 = new KV("a", "BC");
        return kv1.toString();
    }
}
