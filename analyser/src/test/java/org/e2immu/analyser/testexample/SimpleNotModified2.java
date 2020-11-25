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

import org.e2immu.analyser.model.value.PropertyWrapper;
import org.e2immu.analyser.model.value.VariableValue;
import org.e2immu.annotation.E1Immutable;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Almost identical to SimpleNotModified1, but now there's a "barrier" of <code>requireNonNull</code>
 * between <code>input</code> and <code>set</code>. The method introduces a {@link PropertyWrapper}
 * around the {@link VariableValue}, which forces us to use
 * <code>Value.asInstanceOf</code> rather than the <code>instanceof</code> operator.
 * <p>
 * At the same time <code>set</code> has been made explicitly final, reducing complexity.
 * <p>
 * For <code>input</code> to be marked <code>@Modified</code>, {@link org.e2immu.analyser.analyser.ComputeLinking}
 * has to link an already known to be <code>@Modified</code> field <code>set</code> to the parameter.
 */
@E1Immutable
public class SimpleNotModified2 {

    @NotNull
    @Modified
    private final Set<String> set;

    public SimpleNotModified2(@NotNull @Modified Set<String> input) {
        set = Objects.requireNonNull(input);
    }

    @NotModified
    public Stream<String> stream() {
        return set.stream();
    }

    @NotModified
    public Set<String> getSet() {
        return set;
    }

    @Modified
    public void add(String s) {
        set.add(s);
    }
}
