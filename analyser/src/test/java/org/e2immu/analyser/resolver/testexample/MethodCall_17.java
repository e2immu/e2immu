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

import java.util.function.Supplier;

public class MethodCall_17 {

    private void log(String msg, Object... objects) {
        System.out.println(msg + ": " + objects.length);
    }

    private void log(String msg, Object object, Supplier<Object> supplier) {
        System.out.println(msg + ": " + object + " = " + supplier.get());
    }

    public void method(int x) {
        log("Hello!", x, () -> "Return a string");
        log("Hello?", x, "Return a string");
    }
}
