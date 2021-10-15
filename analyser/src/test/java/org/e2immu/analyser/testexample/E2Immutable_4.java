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

import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.Map;

@ERContainer
public class E2Immutable_4 {

    @Linked1(absent = true)
    @Linked(absent = true)
    private final Map<String, String> map4;

    public E2Immutable_4(Map<String, String> map4Param) {
        map4 = new HashMap<>(map4Param); // not linked, no content linking because independent
    }

    public String get4(String input) {
        return map4.get(input);
    }

    @ERContainer
    public Map<String, String> getMap4() {
        return Map.copyOf(map4);
    }
}
