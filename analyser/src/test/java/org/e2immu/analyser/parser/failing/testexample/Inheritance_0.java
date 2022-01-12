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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

public class Inheritance_0 {

    @Container
    abstract static class Y {
        private int i;

        // if we don't make this @Modified, then we'll have problems in MY
        @Modified
        public abstract void increment();

        public int getI() {
            return i;
        }

        public void set(int i) {
            this.i = i;
        }
    }

    @NotModified
    public static int applyToParameterItself(int i, @Modified Y y) {
        y.set(i);
        return y.getI();
    }

    static class MY extends Y {

        @Modified
        @Override
        public void increment() {
            set(getI()+1);
        }
    }

    @NotModified
    public static int applyToParameterMY(int i, @Modified MY y) {
        y.increment();
        return y.getI();
    }

    static class NMY extends Y {

        @NotModified
        @Override
        public void increment() {
            System.out.println("i is "+getI());
        }
    }

    @NotModified
    public static int applyToParameterNMY(int i, @NotModified NMY y) {
        y.increment();
        return y.getI();
    }

    static class NMY_NM extends NMY {

        @NotModified
        @Override
        public void increment() {
            System.out.println("sub; i is "+getI());
        }
    }

    @NotModified
    public static int applyToParameterNMY_NM(int i, @NotModified NMY_NM y) {
        y.increment();
        return y.getI();
    }


    static class NMY_M extends NMY {

        // CAUSES ERROR: doing worse than parent
        @Modified
        @Override
        public void increment() {
            set(getI()+1);
        }
    }

    @NotModified
    public static int applyToParameterNMY_M(int i, @NotModified NMY_M y) {
        y.increment();
        return y.getI();
    }
}
