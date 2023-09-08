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

import org.e2immu.annotation.Identity;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.rare.StaticSideEffects;

/*
 must be with AnnotatedAPI because @IgnoreModifications on System.out
 */

public class StaticSideEffects_5 {

    @StaticSideEffects
    @NotModified
    public static void printStatic(String t) {
        System.out.println("This is " + t);
        String s = t + "abc";
        System.out.println("String is " + s);
    }

    // shows that we can inherit, AND that order does not matter
    @StaticSideEffects
    @NotModified
    public static void printMeViaPrint(String ss) {
        print(ss);
    }

    @StaticSideEffects
    @NotModified
    public static void print(String s) {
        System.out.println("This is " + s);
    }

    @StaticSideEffects
    @NotModified
    public static void printConditionally(String s) {
        if ("abc".equals(s)) {
            System.out.println("This is " + s);
        }
    }

    @StaticSideEffects
    @NotModified
    public static void printForLoop(@NotNull String[] ks) {
        for (String k : ks) {
            System.out.println("This is " + k);
        }
    }

    @StaticSideEffects
    @NotModified
    @Identity
    public static String printMe(String s) {
        System.out.println("This is " + s);
        return s;
    }

    // this method is here to show that we can inherit both annotations
    @StaticSideEffects
    @NotModified
    @Identity
    public static String printMeQuick(String t) {
        return printMe(t);
    }
}

