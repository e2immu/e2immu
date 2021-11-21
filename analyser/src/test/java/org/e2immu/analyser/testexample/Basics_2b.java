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

import java.util.Collection;

/*
Same code as Basics_2b, but analyser now run with the parameter computeContextPropertiesOverAllMethods==true.
The fact that 'string' occurs in a not-null context in 'add' travels to the field, and from there on to the setter.
 */
@MutableModifiesArguments
public class Basics_2b {

    @Variable
    @NotNull
    @Modified(absent = true)
    @NotModified
    private String string;

    @NotNull
    @NotModified
    public String getString() {
        return string;
    }

    @Modified
    public void setString(@NotNull String string) {
        this.string = string;
    }

    @Nullable(absent = true)
    @NotModified
    public void add(@Modified @NotNull Collection<String> collection) {
        collection.add(string); // no warning here, because 'string' is @NotNull
    }
}
