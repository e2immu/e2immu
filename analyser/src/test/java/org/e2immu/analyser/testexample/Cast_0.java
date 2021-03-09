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

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

/*
Example of a cast, but not one that interferes with the immutability rules
 */
@E2Container
public class Cast_0<T> {

    private final T t;

    public Cast_0(T input) {
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
}
