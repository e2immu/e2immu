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
import org.e2immu.annotation.rare.StaticSideEffects;

import java.util.concurrent.atomic.AtomicInteger;

// no static side effects: inside type; modification and assignment inside constructor
@Container
public class StaticSideEffects_1<K> {
    public final K k;
    public final int count;
    @Modified
    @Final(absent = true)
    @Nullable
    private static AtomicInteger counter;

    @StaticSideEffects(absent = true)
    public StaticSideEffects_1(K k) {
        this.k = k;
        if (counter == null) {
            counter = new AtomicInteger();
        }
        count = counter.getAndIncrement();
    }
}
