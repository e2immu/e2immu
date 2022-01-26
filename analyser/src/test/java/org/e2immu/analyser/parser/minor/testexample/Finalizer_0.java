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

import org.e2immu.annotation.Finalizer;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.MutableModifiesArguments;

@MutableModifiesArguments // because of the error method, would have been @Container otherwise
public class Finalizer_0 {

    private int count;
    private String string;

    @Modified
    @Fluent
    public Finalizer_0 set(String s) {
        string = s;
        count++;
        return this;
    }

    @Finalizer
    public String done(String last) {
        return string + "; cnt = " + count + "; last = " + last;
    }

    public static String toStringFinalizer(Finalizer_0 finalizer) {
        return finalizer.toString();
    }

    public static String useFinalizer() {
        return new Finalizer_0().set("a").set("b").done("c");
    }

    /*
    error because a @Finalizer method is called on a parameter
     */
    public static String error(Finalizer_0 finalizer_0) {
        return finalizer_0.set("a").set("b").done("c");
    }
}

