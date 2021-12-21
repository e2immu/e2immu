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

package org.e2immu.analyser.resolver.testexample;

import java.util.List;

public class MethodCall_2 {

    interface Get {
        String get();
    }

    interface Set extends Get {
        void set(String s);
    }

    static class Both implements Set {
        private String s;

        public Both(String s) {
            this.s = s;
        }

        @Override
        public String get() {
            return s;
        }

        @Override
        public void set(String s) {
            this.s = s;
        }
    }

    public void accept(List<Get> list) {
        list.forEach(get -> System.out.println(get.get()));
    }

    public void test() {
        // here, List.of(...) becomes a List<Get> because of the context of 'accept(...)'
        accept(List.of(new Both("hello")));
    }
}
