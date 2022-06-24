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

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/*
from ConstructorCall to Instance, modifying parameter
 */
public class Modification_23 {

    public static int method1(@NotModified Map<String, Integer> in, @NotModified Set<String> set) {
        Map<String, Integer> middle = new HashMap<>(in);
        Set<String> keySet = middle.keySet();
        removeFromSet(keySet, set); // here, middle should change into an instance
        return middle.size();
    }

    public static int method2(@NotModified Map<String, Integer> in, @NotModified Set<String> set) {
        Map<String, Integer> middle = new HashMap<>(in);
        removeFromSet(middle.keySet(), set); // here, middle should change into an instance
        return middle.size();
    }

    private static void removeFromSet(@Modified Set<String> source, @NotModified Set<String> set) {
        source.removeIf(set::contains);
    }
}