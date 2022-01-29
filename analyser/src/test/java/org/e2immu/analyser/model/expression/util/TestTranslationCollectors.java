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

package org.e2immu.analyser.model.expression.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestTranslationCollectors {

    @Test
    public void test() {
        List<String> l1 = List.of("a", "b", "c");
        List<String> trans = l1.stream().collect(TranslationCollectors.toList(l1));
        assertSame(trans, l1);
        List<String> trans2 = l1.stream()
                .map(s -> s.equals("b") ? "bb" : s)
                .collect(TranslationCollectors.toList(l1));
        assertNotEquals(trans2, l1);
    }
}
