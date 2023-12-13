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
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.e2immu.analyser.analyser.LinkedVariables.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
testing the increase in maxIncl
 */
public class TestWeightedGraph_4 extends CommonWG {

    Variable x, a, i, y, b, j;
    WeightedGraph wg1, wg2;
    List<WeightedGraph> wgs;

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

        wg1 = new WeightedGraphImpl();
        wg1.addNode(x, Map.of(x, v0, a, v4));
        wg1.addNode(y, Map.of(y, v0, b, v4));
        wg1.addNode(a, Map.of(a, v0, b, v2, i, v4));
        wg1.addNode(b, Map.of(b, v0, a, v2));
        wg1.addNode(i, Map.of(i, v0, j, v4));
        wg1.addNode(j, Map.of(j, v0));

        wg2 = new WeightedGraphImpl();
        wg2.addNode(x, Map.of(x, v0, a, v4));
        wg2.addNode(a, Map.of(a, v0, b, v2, i, v4));
        wg2.addNode(i, Map.of(i, v0, j, v4));
        wg2.addNode(y, Map.of(y, v0, b, v4));
        wg2.addNode(b, Map.of(b, v0, a, v2));
        wg2.addNode(j, Map.of(j, v0));

        wgs = List.of(wg1, wg2);
    }

    @Test
    public void test1() {
        for (WeightedGraph wg : wgs) {
            Map<Variable, DV> startAtX = wg.shortestPath().links(x, LINK_STATICALLY_ASSIGNED);
            assertEquals(1, startAtX.size());
            assertEquals(v0, startAtX.get(x));
            assertNull(startAtX.get(a));
            assertNull(startAtX.get(i));
        }
    }

    @Test
    public void test2() {
        for (WeightedGraph wg : wgs) {
            Map<Variable, DV> startAtX = wg.shortestPath().links(x, null);
            assertEquals(5, startAtX.size());
            assertEquals(v0, startAtX.get(x));
            assertNull(startAtX.get(y));
            assertEquals(v4, startAtX.get(a));
            assertEquals(v4, startAtX.get(b));
            assertEquals(v4, startAtX.get(i));
            assertEquals(v4, startAtX.get(j));
        }
    }

    @Test
    public void test3() {
        for (WeightedGraph wg : wgs) {
            Map<Variable, DV> startAtToDo = wg.shortestPath().links(a, null);
            assertEquals(4, startAtToDo.size());
            assertNull(startAtToDo.get(x));
            assertNull(startAtToDo.get(y));
            assertEquals(v0, startAtToDo.get(a));
            assertEquals(v2, startAtToDo.get(b));
            assertEquals(v4, startAtToDo.get(i));
            assertEquals(v4, startAtToDo.get(j));
        }
    }
}
