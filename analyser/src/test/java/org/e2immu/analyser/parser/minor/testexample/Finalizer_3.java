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

package org.e2immu.analyser.parser.minor.testexample;

import org.e2immu.annotation.*;
import org.e2immu.support.EventuallyFinal;

public class Finalizer_3 {

    @Container // not @E2, there is no way to mark "data"
    static class OpenEventual {
        @BeforeMark
        private final EventuallyFinal<String> data = new EventuallyFinal<>();

        public OpenEventual set(String string) {
            data.setVariable(string);
            return this;
        }

        public OpenEventual doSomething() {
            System.out.println(data.toString());
            return this;
        }

        public void doSomethingElse() {
            System.out.println(data.toString());
        }

        @Finalizer
        @BeforeMark
        public EventuallyFinal<String> getData() {
            return data;
        }
    }

    @E2Container
    public static EventuallyFinal<String> fluent() {
        EventuallyFinal<String> d = new OpenEventual().set("a").doSomething().set("b").doSomething().getData();
        d.setFinal("x");
        return d;
    }

    @E2Container
    public static EventuallyFinal<String> stepWise() {
        OpenEventual o = new OpenEventual();
        o.set("a");
        o.doSomething();
        o.set("b");
        doSthElse(o); // here we pass it on; forbidden to call the finalizer getData()
        EventuallyFinal<String> d = o.getData();
        d.setFinal("x");
        return d;
    }

    private static void doSthElse(@NotModified OpenEventual openEventual) {
        openEventual.doSomethingElse();
    }

    /*
    Allowed to assign a type with a finalizer to a field IF you construct it yourself, make it effectively final,
    and have a finalizer to finalize it. This is basically a wrapper class.
     */
    @Container
    static class OwnOpenEventual {
        private final OpenEventual openEventual;

        public OwnOpenEventual(String s) {
            openEventual = new OpenEventual().set(s);
        }

        public void set(String s) {
            openEventual.set(s);
            doSthElse(openEventual);
        }

        /* we can only call the finalizer in a finalizer */
        @Finalizer
        @E2Container
        public EventuallyFinal<String> getDataOfOpenEventual() {
            return openEventual.getData();
        }
    }
}
