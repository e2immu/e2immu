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

package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.NotNull;

public class SubTypes_11 {

    @NotNull
    private final String outerField;

    public SubTypes_11(@NotNull String outerField) {
        if(outerField == null) throw new NullPointerException();
        this.outerField = outerField;
    }

    class SubClass {
        private final String innerField; // this one should not cause an error, field is read, but outside the inner class

        SubClass(String innerField) {
            this.innerField = innerField + outerField.charAt(0);
        }

        String getOuter() {
            return SubTypes_11.this.outerField; // this tests the InnerClass.this construct
        }
    }

    public String doSomething(String input) {
        SubClass subClass = new SubClass(input);
        return outerField + " - " + subClass.innerField;
    }

    class SubClassUnusedField {
        private final String unusedInnerField; // ERROR: not used

        SubClassUnusedField(String innerField) {
            this.unusedInnerField = innerField + outerField.charAt(1);
        }
    }

    class SubClassNonPrivateNonFinalField {
        String nonPrivateNonFinal; // ERROR: not final

        public void setNonPrivateNonFinal(String nonPrivateNonFinal) {
            this.nonPrivateNonFinal = nonPrivateNonFinal;
        }

        public String getNonPrivateNonFinal() {
            return nonPrivateNonFinal;
        }
    }

    // in this nested class, we do not care about the modifier...
    private class PrivateSubClassNonPrivateNonFinalField {
        String nonPrivateNonFinalInPrivate; // no error!

        public void setNonPrivateNonFinalInPrivate(String nonPrivateNonFinalInPrivate) {
            this.nonPrivateNonFinalInPrivate = nonPrivateNonFinalInPrivate;
        }

        public String getNonPrivateNonFinalInPrivate() {
            return nonPrivateNonFinalInPrivate;
        }
    }

    static class SubClassAssignmentFromEnclosing {
        private String willBeAssignedFromOutside;

        public void setWillBeAssignedFromOutside(String willBeAssignedFromOutside) {
            this.willBeAssignedFromOutside = willBeAssignedFromOutside;
        }

        public String getWillBeAssignedFromOutside() {
            return willBeAssignedFromOutside;
        }
    }

    // ERROR method should be marked static
    public void doAssignmentIntoNestedType(String s) {
        SubClassAssignmentFromEnclosing a = new SubClassAssignmentFromEnclosing();
        a.willBeAssignedFromOutside = s; // WARN not allowed to assign
    }
}
