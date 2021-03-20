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

import org.e2immu.annotation.*;

/*
 ERROR in M:FinalChecks:0: Condition in 'if' or 'switch' statement evaluates to constant
 ERROR in M:FinalChecks:1: Condition in 'if' or 'switch' statement evaluates to constant
 ERROR in F:s5: Private field not read outside constructors
 */

@E1Immutable(absent = true)
@Container
public class FinalChecks {

    @Final
    private String s3 = "abc";

    private final String s1;

    @Final
    private String s2;

    // because this one is NOT final, the type is not @E1Immutable
    @Final(absent = true)
    private String s4;

    @Final
    private String s5;

    FinalChecks(String s1, String s2) {
        if (s5 == null) {
            s5 = "abc";
        } else {
            // ensure this statement is ignored!!
            s5 = null;
        }
        if (s5 == null) {
            // ensure that this statement is ignored!
            throw new UnsupportedOperationException();
        }
        this.s2 = s2;
        this.s1 = s1 + s3;
    }

    FinalChecks(String s1) {
        this.s1 = s1;
    }

    @Override
    @NotNull
    @NotModified
    public String toString() {
        return s1 + " " + s2 + " " + s3 + " " + s4;
    }

    @Modified
    public void setS4(String s4) {
        this.s4 = s4;
    }
}
