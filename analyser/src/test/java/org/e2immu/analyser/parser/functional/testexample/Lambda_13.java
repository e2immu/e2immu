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

import org.e2immu.annotation.Modified;

import java.util.function.Predicate;

// tests to see if the modification in the Lambda progresses to the field
public class Lambda_13 {

    static class I {
        private int i;

        public void setI(int i) {
            this.i = i;
        }

        public int getI() {
            return i;
        }
    }

    @Modified
    private final I i = new I();

    public Lambda_13(int x) {
        i.setI(x);
    }

    public Predicate<String> nonModifying() {
        return t -> i.getI() < t.length();
    }

    public Predicate<String> modifying() {
        return t -> {
            if (t.length() > 10) i.setI(t.length());
            return i.getI() < t.length();
        };
    }
}
