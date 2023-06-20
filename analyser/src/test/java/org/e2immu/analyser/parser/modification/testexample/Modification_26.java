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
import org.e2immu.annotation.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/*
Part of SMapList which poses a fundamental problem for the current independence computation, resulting in incorrect
modification on 'src'.

e <--2--> src
inDestination --3--> destination
destination <--4--> src
e <--4--> destination

they're all correct, and as a consequence, the weighted graph sequence
    inDestination --> destination --> src
which follows a 3 and therefore allows 4, transfers the modification on inDestination onto src.

The problem is that the knowledge that there is a new LinkedList involved, is lost; modifications on List
should not carry over to 'src', but they do.

 */
public class Modification_26 {

    public static <A, B> boolean addAll(@NotModified Map<A, List<B>> src, @Modified @NotNull Map<A, List<B>> destination) {
        boolean change = false;
        for (Map.Entry<A, List<B>> e : src.entrySet()) {
            List<B> inDestination = destination.get(e.getKey());
            if (inDestination == null) {
                destination.put(e.getKey(), new LinkedList<>(e.getValue()));
                change = true;
            } else {
                if (inDestination.addAll(e.getValue())) { // 1.0.1.1.0
                    change = true;
                }
            }
        }
        return change;
    }
}
