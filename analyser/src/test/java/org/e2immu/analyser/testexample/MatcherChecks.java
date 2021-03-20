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

import java.util.List;

public class MatcherChecks {

    public static String method1(String a1) {
        String s1 = a1;
        if (s1 == null) {
            s1 = "";
        }
        return s1;
    }

    private String s2 = "abc";

    public String method1Negative1(String a1) {
        String s1 = a1;
        if (s1 == null) {
            s2 = "";
            System.out.println(s1);
        }
        return s2;
    }

    public static String method1Negative2(String a1) {
        String s1 = a1;
        if (s1 == null) {
            s1 = "";
        } else {
            s1 = "x";
        }
        return s1;
    }

    public static String method2(String a1) {
        if (a1 == null) {
            return "abc";
        }
        return a1;
    }

    public static String method3(String a1) {
        if ("x".equals(a1)) {
            return "abc";
        } else {
            return a1;
        }
    }

    public static String method4(List<String> strings) {
        for (int i = 0; i < strings.size(); i++) {
            String s = strings.get(i);
            if (s != null && s.length() == 3) return s;
        }
        return "abc";
    }
}
