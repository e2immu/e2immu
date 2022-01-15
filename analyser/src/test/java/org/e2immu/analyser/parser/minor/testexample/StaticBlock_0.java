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

import org.e2immu.annotation.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

// This class is an overview to see where we can put static blocks
// Each subtype has its own static blocks, named as methods $staticBlock$n, n 0 onwards.
// In this example, only the main type StaticBlock_0 has 2 static blocks.

@E1Container // not @E2, because it has a modified (static) field
public class StaticBlock_0 {

    @Final
    @NotNull // no changes after construction in its own static blocks
    @Modified // other types make changes
    private static Map<String, String> map;

    static {
        map = new HashMap<>();
        map.put("1", "2");
        System.out.println("enclosing type");
    }

    @Nullable
    @NotModified
    public static String get(String s) {
        return map.get(s); // should not raise a warning!
    }

    static class SubType {
        static {
            System.out.println("sub-type");
            map.put("2", "3");
        }

        static class SubSubType {
            static {
                System.out.println("sub-sub-type");
                map.put("4", "5");
            }
        }
    }

    class SubType2 {
        static {
            System.out.println("sub-type 2");
            map.put("2", "3");
        }
    }

    @Test
    public void test() {
        System.out.println("Test!");
        SubType2 subType2 = new SubType2();
        SubType.SubSubType subSubType = new SubType.SubSubType();
        // observe that subType has not been created yet!
    }

    static {
        map.put("11", "12"); // should not raise a warning
        System.out.println("2nd part of enclosing type");
    }
}
