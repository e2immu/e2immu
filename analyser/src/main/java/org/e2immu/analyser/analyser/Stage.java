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

package org.e2immu.analyser.analyser;

// suffixes in assignment id; these act as the 3 levels for setProperty
public enum Stage {
    INITIAL("-C"), // C for creation, but essentially, it should be < E
    EVALUATION("-E"), // the - comes before the digits
    MERGE(":M"); // the : comes after the digits
    public final String label;

    Stage(String label) {
        this.label = label;
    }

    public static boolean isPresent(String s) {
        return s != null && (s.endsWith(INITIAL.label) || s.endsWith(EVALUATION.label) || s.endsWith(MERGE.label));
    }

    public static String without(String s) {
        if (isPresent(s)) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    @Override
    public String toString() {
        return label;
    }
}
