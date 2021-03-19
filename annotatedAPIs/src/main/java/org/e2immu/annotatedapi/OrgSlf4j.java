/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
