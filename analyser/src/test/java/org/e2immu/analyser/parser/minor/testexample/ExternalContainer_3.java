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

package org.e2immu.analyser.parser.minor.testexample;

// pretty basic, TRUE all around
// this one should not raise an error: the go method sees "set" first
// as a field without EXT_CONT, then EXT_CONT comes in, but it is also TRUE

import org.e2immu.annotation.Container;

import java.util.Set;

public class ExternalContainer_3 {

    private final Set<String> set = Set.of("Hi!");

    public void go() {
        print(set);
    }

    private static void print(@Container Set<String> in) {
        System.out.println(in);
    }
}
