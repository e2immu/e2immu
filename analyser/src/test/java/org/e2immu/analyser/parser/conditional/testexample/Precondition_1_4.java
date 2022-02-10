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

package org.e2immu.analyser.parser.conditional.testexample;

public class Precondition_1_4 {

    private int i;

    private boolean setPositive4$Precondition(int j3) { return i >= 0 && j3 >= 0; }
    public void setPositive4(int j3) {
        if (i < 0 || j3 < 0) throw new UnsupportedOperationException();
        this.i = j3;
    }

    // this avoids a field not used exception.
    public int getI() {
        return i;
    }

}
