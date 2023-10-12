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

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.NotNull;

import java.util.Map;

// variant, with Y a class rather than an interface
public record Basics_24_1(Map<Integer, Y> map) {

    public Basics_24_1 {
        assert map != null;
    }

    @ImmutableContainer
    static class Y {
    }

    private static class X {
        private Y s;

        public X(Y s) {
            this.s = s;
        }
    }

    public Y method(int pos, Y a, Y y) {
        X x = new X(a);
        x.s = map.getOrDefault(pos, a);
        if (x.s == null) {
            x.s = y;
        }
        return x.s;
    }
}
