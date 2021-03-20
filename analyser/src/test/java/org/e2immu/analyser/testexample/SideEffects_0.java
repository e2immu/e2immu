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

import org.e2immu.annotation.Identity;
import org.e2immu.annotation.NotModified;

/*
 must be with AnnotatedAPI because @NoModifications on System.out

 this one tests "static side effects only"
 */

public class SideEffects_0 {

    @NotModified
    public static void printStatic(String t) {
        System.out.println("This is " + t);
        String s = t + "abc";
        System.out.println("String is " + s);
    }

    // shows that we can inherit, AND that order does not matter
    @NotModified
    public static void printMeViaPrint(String ss) {
        print(ss);
    }

    @NotModified
    public static void print(String s) {
        System.out.println("This is " + s);
    }

    @NotModified
    public static void printConditionally(String s) {
        if ("abc".equals(s)) {
            System.out.println("This is " + s);
        }
    }

    @NotModified
    public static void printForLoop(String[] ks) { // @NullNotAllowed should be here, but we don't have the computation engine yet
        for (String k : ks) {
            System.out.println("This is " + k);
        }
    }

    @NotModified
    @Identity
    public static String printMe(String s) {
        System.out.println("This is " + s);
        return s;
    }

    // this method is here to show that we can inherit both annotations
    @NotModified
    @Identity
    public static String printMeQuick(String t) {
        return printMe(t);
    }
}

