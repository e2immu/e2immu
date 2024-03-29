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

package org.e2immu.analyser.parser.functional.testexample;

import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Variable;

import java.util.function.Predicate;

// tests to see if the assignment in the Lambda progresses to the field
// note: @Variable only when the field analyser is configured to consider the whole primary type.
public class Lambda_13 {

    static class I {
        @Variable
        private int i;

        public int getI() {
            return i;
        }
    }

    @NotModified // outside the scope of modification
    private final I ii = new I();

    public Predicate<String> nonModifying() {
        return t -> ii.getI() < t.length();
    }

    public Predicate<String> assigning() {
        return t -> {
            if (t.length() > 10) ii.i = t.length();
            return ii.getI() < t.length();
        };
    }
}
