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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
Another excerpt from Project_0, causes infinite loop
 */

public class Project_2 {

    static class Container {
        final String value;

        public Container(String value) {
            this.value = value;
        }
    }

    private final Map<String, Container> kvStore = new ConcurrentHashMap<>();

    public String set(String key, String value) {
        Container prev = kvStore.get(key);
        if (prev == null) {
            new Container(value); // removing container also solves the problem
        }
        return prev.value; // cause of the problem (change to key or constant solves the issue)
    }

}

