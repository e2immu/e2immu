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

package org.e2immu.annotatedapi.log;

import org.e2immu.annotation.*;

// explicit @NotModified markers, because in a @Container, for void methods, the default is @Modified
public class OrgSlf4j {
    public static final String PACKAGE_NAME = "org.slf4j";

    @ERContainer
    interface Logger$ {

        @NotModified
        void info(@NotNull String s, Object... objects);

        @NotModified
        void warn(@NotNull String s, Object... objects);

        @NotModified
        void error(@NotNull String s);

        @NotModified
        void error(@NotNull String s, Object object);

        @NotModified
        void error(@NotNull String s, Object object1, Object object2);

        @NotModified
        void error(@NotNull String s, Object... objects);

        @NotModified
        void debug(@NotNull String s, Object object1, Object object2);

        @NotModified
        void debug(@NotNull String s, Object... objects);

        @NotModified
        void debug(@NotNull String s);
    }

    @Container
    @Independent
    interface ILoggerFactory$ {
        @NotNull
        @NotModified
        org.slf4j.Logger getLogger(String name);
    }

    @Container
    @Independent
    interface LoggerFactory$ {
        @NotNull
        @NotModified
        org.slf4j.Logger getLogger(@NotNull Class<?> clazz);

        @NotNull
        @NotModified
        org.slf4j.Logger getLogger(@NotNull String string);

        @NotNull
        @NotModified
        org.slf4j.ILoggerFactory getILoggerFactory();
    }
}
