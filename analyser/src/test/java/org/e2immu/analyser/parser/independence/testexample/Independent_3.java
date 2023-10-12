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

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;

import java.util.stream.Stream;

public class Independent_3 {

    @ImmutableContainer // hc=true default
    interface Expression {

        String get(int key);
    }

    interface HasSwitchLabels {
        // stream is mutable, but independent
        @Independent(hc = true)
        @NotNull
        Stream<Expression> labels();
    }

    static void method(HasSwitchLabels hasSwitchLabels, int j) {
        hasSwitchLabels.labels().forEach(e -> System.out.println(e.get(j)));
    }
}
