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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.Singleton;

@Singleton(absent = true)
public class Singleton_4 {

    public static final Singleton_4 SINGLETON = new Singleton_4(123);
    public static final Singleton_4 SINGLETON_1 = new Singleton_4(9);

    private final int k;

    private Singleton_4(int k) {
        this.k = k;
    }

    public int multiply(int i) {
        return k * i;
    }
}
