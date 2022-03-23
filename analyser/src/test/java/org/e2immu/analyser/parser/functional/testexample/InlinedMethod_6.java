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

// variable field

import org.e2immu.annotation.Constant;

public class InlinedMethod_6 {

    static class VariableField {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }

        private int sum(int j) {
            return i + j;
        }

        public int expandSum(int k) {
            return k * sum(3); // sum should be expanded here
        }
    }

    // because it is a variable field, we do not link the field i to the parameter of setI (we *could*)
    @Constant(absent = true)
    public static int expand() {
        VariableField variableField = new VariableField();
        variableField.setI(3);
        return variableField.getI(); // variable field expansion still allowed, same primary type
    }
}
