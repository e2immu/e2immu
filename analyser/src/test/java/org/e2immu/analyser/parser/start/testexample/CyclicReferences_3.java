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

import org.e2immu.annotation.NotModified;

public class CyclicReferences_3 {

    @NotModified
    public static boolean methodC(String paramC) {
        if ("b".equals(paramC)) return methodD(paramC);
        return "a".equals(paramC);
    }

    @NotModified
    public static boolean methodD(String paramD) {
        if ("a".equals(paramD)) return methodE(paramD);
        return "b".equals(paramD);
    }

    @NotModified
    public static boolean methodE(String paramE) {
        if ("b".equals(paramE)) return methodF(paramE);
        return "a".equals(paramE);
    }

    @NotModified
    public static boolean methodF(String paramF) {
        if ("a".equals(paramF)) return methodC(paramF);
        return "b".equals(paramF);
    }
}
