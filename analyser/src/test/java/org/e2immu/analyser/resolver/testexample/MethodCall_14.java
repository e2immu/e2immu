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

import java.util.Arrays;

public class MethodCall_14 {
    record Pair<K, V>(K k, V v) {
    }

    interface VI {
        E make();
    }

    static class VII implements VI {

        @Override
        public E make() {
            return null;
        }
    }

    interface E {
        boolean test();
    }

    static class EE implements E {

        @Override
        public boolean test() {
            return false;
        }
    }

    public void accept(boolean b, E... values) {
    }

    // sort of mirrors the notNullValuesAsExpression method in StatementAnalysis
    public void method(VI b, E e, VI... vis) {
        accept(true, Arrays.stream(vis)
                .filter(vi -> vi.getClass().toString().contains("e"))
                .map(vi -> {
                    if (b instanceof VII vii) {
                        if (e instanceof EE ee) {
                            return new Pair<>(vi, e.test() + "xxx");
                        }
                        String s = "ab";
                        return new Pair<>(vi, s);
                    }
                    return null;
                })
                .filter(p -> p != null && p.k.make() != null && p.v.length() == 4)
                .map(p -> p.k.make())
                .toArray(E[]::new));
    }
}
