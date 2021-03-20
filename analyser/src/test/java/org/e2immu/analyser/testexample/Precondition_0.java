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

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

public class Precondition_0 {

    public static boolean either$Precondition(String e1, String e2) { return e1 != null || e2 != null; }
    public static String either(String e1, String e2) {
        if (e1 == null && e2 == null) throw new UnsupportedOperationException();
        return e1 + e2;
    }

    @NotNull
    public static String useEither1(@NotNull String in1) {
        return either(in1, null);
    }

    @NotNull
    public static String useEither2(@NotNull String in2) {
        return either(null, in2);
    }


    // here we want to propagate the precondition from MethodValue down to the method,
    // very much like we propagate the single not-null

    public static boolean useEither3$Precondition(String f1, String f2) { return f1 != null || f2 != null; }
    public static String useEither3(@Nullable String f1, @Nullable String f2) {
        return either(f1, f2);
    }

}
