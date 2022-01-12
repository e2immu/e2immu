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

package org.e2immu.analyser.parser.failing.testexample;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

public class ExampleManualSelfModificationOnForEach {

    @Test
    public void testDangerous() {
        List<String> l1 = new ArrayList<>();
        Collections.addAll(l1, "a", "c", "e");
        try {
            print(l1);
            fail();
        } catch(ConcurrentModificationException cme) {
            // OK
        }
    }

    static void print(List<String> list) {
        list.forEach(l -> {
            System.out.println(l);
            if (l.startsWith("a")) {
                list.add("b");
            }
        });
    }
}
