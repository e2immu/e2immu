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

package org.e2immu.analyser.parser.basics.testexample;

import org.e2immu.annotation.*;

@Container
public class Basics_3 {

    @Nullable
    @Variable
    @NotModified
    private String s;

    @Modified
    public void setS1(@NotNull String input1) {
        if (input1.contains("a")) {
            // APIs are not loaded, so should produce a potential null pointer warning
            System.out.println("With a " + s);
            s = "xyz";
        } else {
            s = "abc";
        }
        assert s != null; // should produce a warning
    }

    @Modified
    public void setS2(@Nullable String input2) {
        s = input2;
        // nullable
    }

    @NotModified
    @Nullable
    public String getS() {
        return s;
    }
}

