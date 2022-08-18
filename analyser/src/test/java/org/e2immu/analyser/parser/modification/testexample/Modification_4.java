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

package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;

import java.util.Set;

/*
@Modified travels from local4, statement 1 (A) to its effectively final content locally (B) to the real field (C)
to the parameter in4 (D).

In the same way, @NotNull travels from local4 to its effectively final content set4 locally, then
to the field itself, then to the parameter in4.

Effectively final is declared in the field analyser, after iteration 0 in the statement analyser.

In iteration 0, local4 will have a value of NO_VALUE, as set4 is not known.
The field analyser declares set4 as effectively final, and assigns the value in4, and links it to the parameter.

In iteration 1, therefore, set4 will have a value, and local4 can be assigned to set4

 */
@FinalFields
@Container(absent = true)
public class Modification_4 {

    @Modified
    @NotNull
    private final Set<String> set4;

    public Modification_4(@Modified @NotNull Set<String> in4) {
        this.set4 = in4;
    }

    @Modified
    public void add4(@NotNull String v) {
        Set<String> local4 = set4;
        local4.add(v); // this statement induces a @NotNull on in4
    }
}
