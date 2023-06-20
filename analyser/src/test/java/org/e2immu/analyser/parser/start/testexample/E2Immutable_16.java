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

package org.e2immu.analyser.parser.start.testexample;


import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Modified;

import java.util.List;

@ImmutableContainer
public class E2Immutable_16 {

    @ImmutableContainer(hc = true)
    interface OneVariable {
        Variable variable();
    }

    @ImmutableContainer(hc = true)
    interface Variable extends OneVariable {

    }

    @ImmutableContainer(hc = true)
    record Record(OneVariable x) {

        @Override
        public String toString() {
            return "Record{" + "x=" + x.variable() + '}';
        }
    }

    @FinalFields
    record LocalVariable(List<String> readIds, Variable variable) implements Variable {

        @Modified // computed, causes error!
        @Override
        public Variable variable() {
            readIds.add("1");
            return variable;
        }
    }
}
