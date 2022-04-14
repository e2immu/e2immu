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


import org.e2immu.annotation.ERContainer;
import org.e2immu.annotation.Modified;

import java.util.List;

/* Current view (20220413) on why OneVariable and Variable are MUTABLE, NOT_CONTAINER:
 * Adding e.g. @E2Immutable via a contract will restrict implementations. Not adding it leaves implementations
 * free to choose. However, if you do not add @Modified on the method (variable()), your implementation cannot be modifying!
 */
@ERContainer
public class E2Immutable_16 {

    interface OneVariable {
        Variable variable();
    }

    interface Variable extends OneVariable {

    }

    record Record(OneVariable x) {

        @Override
        public String toString() {
            return "Record{" + "x=" + x.variable() + '}';
        }
    }

    record LocalVariable(List<String> readIds, Variable variable) implements Variable {

        @Modified // computed, causes error!
        @Override
        public Variable variable() {
            readIds.add("1");
            return variable;
        }
    }
}
