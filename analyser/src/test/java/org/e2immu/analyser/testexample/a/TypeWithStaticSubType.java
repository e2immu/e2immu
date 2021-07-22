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

package org.e2immu.analyser.testexample.a;

public class TypeWithStaticSubType {

    public static final class C1 {
        public static final int CONSTANT = 33;
    }

    public static final class C2 {
        public static final int CONSTANT = 34;
    }

    public static final class SubType1 {
        private final int divisor;

       public SubType1(int divisor) {
            this.divisor = divisor;
        }

        public int doSomething(int i) {
            return i / divisor;
        }
    }

    public interface SubType2 {
        int doSomething(int i);
    }
}
