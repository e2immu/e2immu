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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;


public class Modification_9 {
    private static final Logger LOGGER = LoggerFactory.getLogger(Modification_9.class);

    @Modified // modified by add method
    final Set<String> s2 = new HashSet<>();

    @Modified // modifies s2
    public int add(String s) {
        Set<String> theSet = s2; // linked to s2, which is linked to set2
        LOGGER.debug("The set has {} elements before adding {}", theSet.size(), s);
        theSet.add(s); // this one modifies s2!
        return 1;
    }

}
