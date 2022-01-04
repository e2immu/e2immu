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
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// test the requirement on the interface for log to be identity.

public class Identity_4 {

    @NotNull
    static final Logger LOGGER = LoggerFactory.getLogger(Identity_4.class);

    interface LogMe<T> {

        @Identity
        @NotNull
        T log(@NotNull T t);
    }

    static class LogMe1<T> implements LogMe<T> {
        @Override
        public T log(T t) {
            LOGGER.debug(t.toString());
            return t;
        }
    }

    static class LogMeError implements LogMe<String> {

        // ERROR: method is not @Identity, as required by LogMe.log()
        @Override
        public String log(String t) {
            LOGGER.debug(t);
            return "hello";
        }
    }
}
