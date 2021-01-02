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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@E1Container
public class Container_5 {

    @NotModified(absent = true)
    @Modified
    @Linked(absent = true)
    @NotNull
    private final List<String> list;

    @Independent
    public Container_5() {
        list = new ArrayList<>();
    }

    @Independent
    public Container_5(@NotNull Collection<String> coll5) {
        this();
        addAll5(coll5);
    }

    @Modified
    public void add(String string) {
        this.list.add(string);
    }

    @NotModified(absent = true)
    public void addAll5(@NotNull1 Collection<String> collection) {
        list.addAll(collection);
    }

    @NotModified
    // note: a t m we do not want @NotModified on consumer, because it is @NotModified by default (functional interface)
    public void visit(@NotNull1 Consumer<String> consumer) {
        list.forEach(consumer);
    }
}


