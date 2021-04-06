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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Finalizer;
import org.e2immu.annotation.MutableModifiesArguments;

import java.util.concurrent.atomic.AtomicReference;

@MutableModifiesArguments // because of the error method
public class Finalizer_0 {

    private int count;
    private final AtomicReference<String> string = new AtomicReference<>();

    public Finalizer_0 set(String s) {
        string.set(s);
        count++;
        return this;
    }

    @Finalizer(contract = true)
    public String done(String last) {
        return string + "; cnt = " + count + "; last = " + last;
    }

    public static String toStringFinalizer(Finalizer_0 finalizer) {
        return finalizer.toString();
    }

    public static String useFinalizer() {
        return new Finalizer_0().set("a").set("b").done("c");
    }

    public static String error(Finalizer_0 finalizer_0) {
        return finalizer_0.set("a").set("b").done("c");
    }
}

