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

import org.e2immu.annotation.*;
import org.e2immu.annotation.rare.IgnoreModifications;
import org.e2immu.annotation.rare.StaticSideEffects;

@Container
public class StaticSideEffects_2<K> {
    private final K k;

    private static int counter;
    @IgnoreModifications
    private static int counter2;

    @StaticSideEffects
    public StaticSideEffects_2(K k) {
        this.k = k;
        System.out.println("k is " + k);
    }

    @StaticSideEffects(absent = true)
    @Modified
    public K getK() {
        ++counter;
        return k;
    }

    @StaticSideEffects
    @Modified
    public K getK2() {
        ++counter2;
        return k;
    }

    public static int countAccessToK() {
        return counter;
    }
}
