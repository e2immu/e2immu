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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.e2immu.analyser.analyser.LinkedVariables.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
testing the increase in maxIncl
 */
public class TestWeightedGraph_4 {

    Variable x, a, i, y, b, j;
    final DV v0 = LINK_STATICALLY_ASSIGNED;
    final DV v2 = LINK_DEPENDENT;
    final DV v3 = LINK_IS_HC_OF;
    final DV v4 = LINK_COMMON_HC;
    WeightedGraph wg;

    /*
     x ---3---> a ---3---> i
                ^          |
                |2         |4
                v          v
     y ---3---> b          j
     */
    @BeforeEach
    public void beforeEach() {
        x = makeVariable("x"); // type X
        y = makeVariable("y"); // type X
        a = makeVariable("a"); // type List<X>
        b = makeVariable("b"); // type List<X>
        i = makeVariable("i"); // type List<List<X>>
        j = makeVariable("j"); // type List<List<X>>

        wg = new WeightedGraph();
        wg.addNode(x, Map.of(x, v0, a, v3));
        wg.addNode(y, Map.of(y, v0, b, v3));
        wg.addNode(a, Map.of(a, v0, b, v2, i, v3));
        wg.addNode(b, Map.of(b, v0, a, v2));
        wg.addNode(i, Map.of(i, v0, j, v4));
        wg.addNode(j, Map.of(j, v0));
    }

    @Test
    public void test1() {
        Map<Variable, DV> startAtToDo = wg.links(x, LINK_STATICALLY_ASSIGNED, false);
        assertEquals(1, startAtToDo.size());
        assertEquals(v0, startAtToDo.get(x));
        assertNull(startAtToDo.get(a));
        assertNull(startAtToDo.get(i));
    }

    @Test
    public void test2() {
        Map<Variable, DV> startAtToDo = wg.links(x, LINK_IS_HC_OF, false);
        assertEquals(5, startAtToDo.size());
        assertEquals(v0, startAtToDo.get(x));
        assertNull(startAtToDo.get(y));
        assertEquals(v3, startAtToDo.get(a));
        assertEquals(v3, startAtToDo.get(b));
        assertEquals(v3, startAtToDo.get(i));
        assertEquals(v4, startAtToDo.get(j));
    }

    @Test
    public void test3() {
        Map<Variable, DV> startAtToDo = wg.links(a, LINK_IS_HC_OF, false);
        assertEquals(4, startAtToDo.size());
        assertNull(startAtToDo.get(x));
        assertNull(startAtToDo.get(y));
        assertEquals(v0, startAtToDo.get(a));
        assertEquals(v2, startAtToDo.get(b));
        assertEquals(v3, startAtToDo.get(i));
        assertEquals(v4, startAtToDo.get(j));
    }

    private Variable makeVariable(String name) {
        TypeInfo t = new TypeInfo("a.b.c", "T");
        return new LocalVariableReference(new LocalVariable(name, new ParameterizedType(t, 0)));
    }
}
