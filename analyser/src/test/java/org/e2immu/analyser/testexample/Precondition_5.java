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

// reverse of Precondition_1, but should work in exactly the same way!

public class Precondition_5 {

    private int i;

    boolean setPositive1$Precondition() { return i >= 0; }
    public void setPositive1(int j1) {
        if(i >= 0) {
            this.i = j1;
        }
        throw new UnsupportedOperationException();
    }

    static boolean setPositive2$Precondition(int j1) { return j1 >= 0; }
    public void setPositive2(int j1) {
        if (j1 >= 0) {
            this.i = j1;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private boolean setPositive4$Precondition(int j3) { return i >= 0 && j3 >= 0; }
    public void setPositive4(int j3) {
        if (i >= 0 && j3 >= 0) this.i = j3; else throw new UnsupportedOperationException();
    }

    // this avoids a field not used exception.
    public int getI() {
        return i;
    }

}
