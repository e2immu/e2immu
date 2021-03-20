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

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

public class ObjectFlow3 {

    static class Config {
        final boolean debug;
        final int complexity;

        @Modified
        public Config(boolean debug, int complexity) {
            this.debug = debug;
            this.complexity = complexity;
        }

        @Override
        public String toString() {
            return "debug: " + debug + ", complexity " + complexity;
        }
    }

    public static void main(String[] args) {
        Config config = new Config(Boolean.parseBoolean(args[0]), Integer.parseInt(args[1]));
        new Main(config).go();
    }

    static class Main {
        public final Config config;

        @Modified
        public Main(Config config) {
            this.config = config;
        }

        @NotModified
        public void go() {
           InBetween inBetween = new InBetween(config);
           inBetween.go();
        }
    }

    static class InBetween {
        private final Config config;

        @Modified
        InBetween(Config config) {
            this.config = config;
        }

        @NotModified
        void go() {
            DoSomeWork doSomeWork = new DoSomeWork(config);
            doSomeWork.go();
        }
    }

    static class DoSomeWork {
        private final Config config;

        DoSomeWork(Config config) {
            this.config = config;
        }

        @NotModified
        void go() {
            assert config != null;
        }
    }
}
