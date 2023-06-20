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

package org.e2immu.analyser.resolver.testexample;


import org.e2immu.annotation.Final;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.eventual.Mark;

@ImmutableContainer(after = Basics_3.STRING)
public class Basics_3 {

    public static final String STRING = "hello";

    @Final(after = STRING)
    @FinalFields(after = Basics_3.STRING)
    private String field;

    @Mark(STRING)
    public void method() {
        // don't need anything here
    }
}
