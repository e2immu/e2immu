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

import org.e2immu.annotation.Identity;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

public class Warnings_13 {

    // PART 1 -- cannot provoke error because the @NotNull travels to the parameter

    @Identity
    @NotModified
    public static int method1(@NotNull Integer in) {
        return in;
    }

    // PART 2 -- the cast to 'int' does not travel to the parameter in the correct analyser setting

    @Nullable
    private final Integer i;

    public Warnings_13(Integer ip) {
        i = ip;
    }

    public int method2() {
        return i; // must raise potential null pointer warning
    }
}
