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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;

/*
See explanation in ExternalContainer_0.
In this example, the outer type is not a container.
An error will be raised in the print() call in go.
 */

@E2Immutable(recursive = true)
public class ExternalContainer_1 {


    interface External {
    }

    private final External external;

    public ExternalContainer_1() {
        external = new External() {
        };
    }

    public void go() {
        print(external);
    }

    private static void print(@Container External external) {
        System.out.println(external);
    }
}
