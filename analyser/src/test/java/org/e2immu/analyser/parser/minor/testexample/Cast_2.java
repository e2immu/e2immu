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

package org.e2immu.analyser.parser.minor.testexample;

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

/*
Example of a cast which messes with the immutability rules:
 */
@E2Container
public class Cast_2<T> {

    private final T t;

    // @NotNull thanks to the option 'computeContextPropertiesOverAllMethods' in the test
    public Cast_2(@NotNull T input) {
        t = input;
    }

    @NotModified
    public T getT() {
        return t;
    }

    @NotModified
    public String getTAsString() {
        return t.toString();
    }
}
