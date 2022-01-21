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
import org.e2immu.annotation.ERContainer;

/*
Whether implementations of External are @Container, or not, depends on
whether ExternalContainer_0 is a @Container, or not.

In this example, it is, and there will be no error on the print() call in go.

Inside methods, the field "external" starts off as @Container.
It is the job of the EXTERNAL_CONTAINER property to travel from the field
into the methods, to potentially raise an issue.
 */
@ERContainer
public class ExternalContainer_0 {


    interface External {
    }

    private final External external;

    public ExternalContainer_0() {
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
