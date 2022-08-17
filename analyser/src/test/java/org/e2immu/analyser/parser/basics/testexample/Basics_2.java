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

package org.e2immu.analyser.parser.basics.testexample;

import org.e2immu.annotation.*;

import java.util.Collection;

/*
there is competition between the @NotNull implied on string in the add method,
and the empty initialiser on the variable field.
The result must be that string is @Nullable.
 */
@Container(absent = true)
@Constant(absent = true)
@Immutable(absent = true)
public class Basics_2 {

    @Variable
    @Nullable
    @Modified(absent = true)
    @NotModified
    private String string;

    @Nullable
    @NotModified
    public String getString() {
        return string;
    }

    @Modified
    public void setString(@Nullable String string) {
        this.string = string;
    }

    @Nullable(absent = true)
    @NotModified
    public void add(@Modified @NotNull Collection<String> collection) {
        collection.add(string); // expect potential null pointer exception here for string
    }
}
