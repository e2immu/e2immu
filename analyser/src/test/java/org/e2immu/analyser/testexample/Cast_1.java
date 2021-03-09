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

import org.e2immu.annotation.E1Immutable;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

/*
so while T is implicitly immutable, this promise is broken by the use
of a cast in the incrementedT method
 */
@E1Immutable
public class Cast_1<T> {

    static class Counter {
        private int i = 0;

        public int increment() {
            return ++i;
        }
    }

    @Modified
    private final T t;

    public Cast_1(@Modified T input) {
        t = input;
    }

    @NotModified
    public T getT() {
        return t;
    }

    @NotModified
    public String getTAsString() {
        return (String) t;
    }

    @Modified
    public int incrementedT() {
        return ((Counter) t).increment();
    }
}
