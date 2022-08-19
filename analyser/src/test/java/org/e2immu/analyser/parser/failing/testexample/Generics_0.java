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

import org.e2immu.annotation.*;

import java.util.Collection;
import java.util.List;

public class Generics_0 {

    // X mutable!

    @Container
    @Independent
    static class X {
        int i;

        @NotModified
        public int getI() {
            return i;
        }

        @Modified
        public void setI(int i) {
            this.i = i;
        }

        @Modified
        public void increment() {
            this.i++;
        }
    }

    @Container
    @FinalFields
    static class XSTransparent {

        private final List<X> list;

        public XSTransparent(@Independent Collection<X> collection) {
            list = List.copyOf(collection);
        }

        public List<X> getList() {
            return list;
        }
    }

    @FinalFields // @Dependent
    static class XSRead {

        @Modified
        private final List<X> list;

        public XSRead(Collection<X> collection) {
            list = List.copyOf(collection);
        }

        public List<X> getList() {
            return list;
        }

        @NotModified
        public int getFirstI() {
            X first = list.get(0);
            return first.getI();
        }
    }

    @FinalFields // @Dependent
    static class XSWrite {

        @Modified
        private final List<X> list;

        public XSWrite(Collection<X> collection) {
            list = List.copyOf(collection);
        }

        public List<X> getList() {
            return list;
        }

        @Modified
        public void incrementFirst() {
            X first = list.get(0);
            first.increment();
        }
    }
}
