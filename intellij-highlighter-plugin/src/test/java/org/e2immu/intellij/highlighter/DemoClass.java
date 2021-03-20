/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.intellij.highlighter;

import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Set;

/*

e2i.settings.colors.attr.independent-m=@Independent method
e2i.settings.colors.attr.-m=Unannotated method

e2i.settings.colors.attr.modified-f=@Modified field
e2i.settings.colors.attr.e2immutable-f=@E2Immutable field
e2i.settings.colors.attr.e2container-f=@E2Container field
e2i.settings.colors.attr.final-f=@Final field
e2i.settings.colors.attr.-f=Unannotated field


 */
// E2Immutable (not a container!)
public class DemoClass {

    private int count; // count = variable field
    // Set, HashSet = container, String = E2Container; strings = modified
    private final Set<String> strings = new HashSet<>();

    // input = non-modified parameter, count = not annotated parameter
    // ImmutableSet = E2Container, Integer = not annotated type
    public DemoClass(ImmutableSet<Integer> input, int count) {
        this.count = count;
        // add = modifying method, toString = nonModifying method; Integer = unannotated type
        input.forEach(i -> strings.add(Integer.toString(i)));
    }

    // destination = modified parameter; addTo = not modified+independent method
    public void addTo(Set<String> destination) {
        destination.addAll(strings);
    }

    // modifying method
    public void setCount(int count) {
        this.count = count;
    }
}
