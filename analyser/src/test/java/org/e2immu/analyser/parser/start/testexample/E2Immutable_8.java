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

package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotModified;

import java.util.HashMap;
import java.util.Map;

@FinalFields @Container
public class E2Immutable_8 {

    @Independent
    @Container
    static class SimpleContainer {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    @NotModified
    private final Map<String, SimpleContainer> map8;

    public E2Immutable_8(Map<String, SimpleContainer> map8Param) {
        map8 = map8Param; // dependent
    }

    // dependent! (simple container is modifiable)
    public SimpleContainer get8(String input) {
        return map8.get(input);
    }

    // dependent!
    public Map<String, SimpleContainer> getMap8() {
        Map<String, SimpleContainer> incremented = new HashMap<>(map8);
        incremented.values().forEach(sc -> sc.setI(sc.getI() + 1));
        return incremented;
    }
}

