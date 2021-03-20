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

import org.e2immu.annotation.Identity;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Identity_2 {
    /*
    The @NotNull on the idemX methods relies on LOGGER.debug(@NotNull String s) { .. }
     */
    @NotNull
    static final Logger LOGGER = LoggerFactory.getLogger(Identity_2.class);

    @Identity
    @NotModified
    @NotNull
    public static String idem(@NotNull String s) {
        LOGGER.debug(s);
        return s;
    }

    @Identity
    @NotModified
    public static String idem2(String s, String t) {
        LOGGER.debug(s + " " + t);
        return idem(s);
    }

    @Identity
    @NotModified
    @NotNull
    public static String idem3(String s) {
        LOGGER.debug(s);
        if ("a".equals(s)) {
            return idem(idem2(s, "abc"));
        } else {
            return s;
        }
    }
}
