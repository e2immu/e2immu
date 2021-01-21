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

import org.e2immu.annotation.*;

import java.util.Set;


// probably more interesting of its @NotNull on p
// the statement time does not increase going from statement 0 to the reading of s (before add!)
// the @NotNull on the local copy must be on the field's value as well

@Container(absent = true)
@MutableModifiesArguments
public class Container_0 {

    @Nullable
    private Set<String> s;

    @Modified
    public void setS(@Modified @NotNull Set<String> p, String toAdd) {
        this.s = p;
        this.s.add(toAdd);
    }

    public Set<String> getS() {
        return s;
    }
}