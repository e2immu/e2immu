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

package org.e2immu.analyser.parser.independence.testexample;

import org.e2immu.annotation.Independent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/*
Example of supplier linking
 */
public class Independent1_10<T> {
    private final List<T> list = new ArrayList<>();

    @Independent
    public static <T> Independent1_10<T> of(@Independent(hcReturnValue = true) T... ts) {
        Independent1_10<T> result = new Independent1_10<>();
        result.fill(new Supplier<>() {
            int i;

            @Override
            @Independent // TODO we have no way of marking that we're linking to a parameter
            public T get() {
                return i < ts.length ? ts[i++] : null;
            }
        });
        return result;
    }

    private void fill(@Independent(hc = true) Supplier<T> supplier) {
        T t;
        while ((t = supplier.get()) != null) list.add(t);
    }

    @Independent(hc = true)
    public List<T> getList() {
        return list.stream().toList();
    }
}
