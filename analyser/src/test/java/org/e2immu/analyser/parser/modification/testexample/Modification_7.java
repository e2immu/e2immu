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

package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.*;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Basic example of modification travelling from the method <code>add</code>
 * to the field <code>set</code>, then to the parameter <code>input</code>.
 * <p>
 * At the same time, the not-null property travels along.
 */
@FinalFields
@Container(absent = true)
public class Modification_7 {

    @Modified
    @Final
    private Set<String> set;

    public Modification_7(@Modified Set<String> input) {
        set = input;
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
        if (set != null)
            set.add(s);
    }
}
