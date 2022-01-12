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
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.ERContainer;
import org.e2immu.annotation.Independent;

import java.util.HashMap;
import java.util.Map;

@E2Container
public class E2Immutable_6 {

    // SimpleContainer can be replaced by an unbound parameter type in this example

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

    private final Map<String, SimpleContainer> map6;

    public E2Immutable_6(Map<String, SimpleContainer> map6Param) {
        map6 = new HashMap<>(map6Param); // not linked
    }

    public SimpleContainer get6(String input) {
        return map6.get(input);
    }

    @ERContainer // because simpleContainer is transparent, and result of copyOf is E2Immutable
    public Map<String, SimpleContainer> getMap6() {
        return Map.copyOf(map6);
    }
}
