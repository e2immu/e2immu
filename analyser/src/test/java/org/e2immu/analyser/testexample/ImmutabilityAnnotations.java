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

import com.google.common.collect.ImmutableSet;
import org.e2immu.annotation.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The purpose of this class is to show all immutability annotations colored in the highlighter,
 * working towards the first open source milestone.
 * <p>
 * For types:
 * <ul>
 *     <li>@E2Container  OK String</li>
 *     <li>@E2Immutable</li>
 *     <li>@E1Container</li>
 *     <li>@E1Immutable  OK</li>
 *     <li>@MutableModifiesArguments</li>
 *     <li>@Container  OK List, Set, ...</li>
 * </ul>
 *
 * <p>
 * For constructors:
 * <ul>
 *     <li>@Independent</li>
 *     <li>@Dependent</li>
 * </ul>
 * <p>
 * For methods:
 * <ul>
 *     <li>@Independent  OK</li>
 *     <li>@Dependent  OK</li>
 *     <li>@NotModified OK</li>
 *     <li>@Modified  OK</li>
 * </ul>
 * additionally on the return type, the dynamic type annotations
 * <ul>
 *     <li>@E2Container  OK</li>
 *     <li>@E1Container</li>
 *     <li> @E2Immutable </li>
 *     <li>@E1Immutable</li>
 *     <li> @BeforeMark</li>
 * </ul>
 * <p>
 * For fields:
 * <ul>
 *     <li>@Variable  OK</li>
 *     <li>@SupportData (implying @NotModified in a type which is (eventually) @E1Immutable)</li>
 *     <li>@Modified</li>
 *     <li>@NotModified</li>
 * </ul>
 * <p>
 * For parameters:
 * <li>
 *     <ul>@Modified  OK</ul>
 *     <ul>@NotModified  OK</ul>
 * </li>
 */

@E1Immutable(after = "mark")
public class ImmutabilityAnnotations {

    @Variable
    private boolean frozen;

    @SupportData // implying @NotModified type @E1Immutable (which it will be)
    private Set<String> strings;

    @Independent
    public ImmutabilityAnnotations(List<String> list) {
        this.strings = new HashSet<>(list);
    }

    @Dependent
    public ImmutabilityAnnotations(Set<String> set) {
        this.strings =set;
    }

    @NotModified // but not @Independent, because not @SupportData
    public boolean isFrozen() {
        return frozen;
    }

    @Modified
    @Mark("mark")
    public void freeze() {
        if (frozen) throw new UnsupportedOperationException();
        frozen = true;
    }

    @Modified
    @Only(before = "mark")
    public void addStrings(@NotModified Collection<String> input) {
        if (frozen) throw new UnsupportedOperationException();
        this.strings.addAll(input);
    }

    @Dependent
    public Set<String> mix(@Modified Set<String> mixer) {
        mixer.addAll(strings);
        return strings;
    }

    @Independent
    @E2Container
    public Set<String> copy() {
        return ImmutableSet.copyOf(strings);
    }
}
