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

package org.e2immu.annotatedapi;

import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

public class OrgSlf4j {
    public static final String PACKAGE_NAME = "org.slf4j";

    static class Logger$ {
        final public String ROOT_LOGGER_NAME = "ROOT";

        /*
        The reason we want to add @NotModified on the methods, is that these
        modifications are outside of the scope of what we're interested in.
         */
        @NotModified
        public void info(@NotNull String s, @NotModified Object... objects) {
        }

        @NotModified
        public void warn(@NotNull String s, @NotModified Object... objects) {
        }

        @NotModified
        public void error(@NotNull String s, @NotModified Object... objects) {
        }

        @NotModified
        public void debug(@NotNull String s, @NotModified Object... objects) {
        }

        @NotModified
        public void debug(@NotNull String s) {
        }
    }

    interface ILoggerFactory$ {
        @NotNull
        public org.slf4j.Logger getLogger(String name);
    }

    static class LoggerFactory$ {
        @NotNull
        public static org.slf4j.Logger getLogger(@NotNull Class<?> clazz) {
            return null;
        }

        @NotNull
        public static org.slf4j.Logger getLogger(@NotNull String string) {
            return null;
        }

        @NotNull
        public static org.slf4j.ILoggerFactory getILoggerFactory() {
            return null;
        }
    }
}
