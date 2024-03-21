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

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.e2immu.analyser.analyser.LinkedVariables.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
testing the increase in maxIncl
 */
public class TestWeightedGraph_6 extends CommonWG {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestWeightedGraph_6.class);

    Variable x, e, se, s, lt;
    List<ShortestPath> sps;

    @BeforeEach
    public void beforeEach() {
        x = makeVariable("x");
        e = makeVariable("e");
        se = makeVariable("s.e");
        s = makeVariable("s");
        lt = makeVariable("lt");

        WeightedGraph wg1 = new WeightedGraphImpl();
        wg1.addNode(x, Map.of(e, v0, se, v0));
        wg1.addNode(se, Map.of(x, v0, e, v2, s, v2));
        wg1.addNode(e, Map.of(x, v0, se, delay));
        wg1.addNode(s, Map.of());
        wg1.addNode(lt, Map.of());

        ShortestPathImpl sp1 = (ShortestPathImpl) wg1.shortestPath();
        testSorted(sp1);

        // produces a different sorting, different order
        WeightedGraph wg2 = new WeightedGraphImpl();
        wg2.addNode(x, Map.of(e, v0, se, v0));
        wg2.addNode(se, Map.of(x, v0, e, v2, s, v2));
        wg2.addNode(e, Map.of(x, v0, se, delay));
        wg2.addNode(s, Map.of());
        wg2.addNode(lt, Map.of());

        ShortestPathImpl sp2 = (ShortestPathImpl) wg2.shortestPath();
        testSorted(sp2);

        // produces a different sorting, different order
        WeightedGraph wg3 = new WeightedGraphImpl();
        wg3.addNode(x, Map.of(se, v0, e, v0));
        wg3.addNode(se, Map.of(x, v0, e, v2, s, v2));
        wg3.addNode(e, Map.of(x, v0, se, delay));
        wg3.addNode(s, Map.of());
        wg3.addNode(lt, Map.of());

        ShortestPathImpl sp3 = (ShortestPathImpl) wg3.shortestPath();
        testSorted(sp3);

        sps = List.of(sp1, sp2, sp3);
    }

    // NOTE: reverse string sort (last letter first)
    private void testSorted(ShortestPathImpl sp1) {
        assertEquals(e, sp1.variablesGet(0));
        assertEquals(se, sp1.variablesGet(1));
        assertEquals(s, sp1.variablesGet(2));
        assertEquals(lt, sp1.variablesGet(3));
        assertEquals(x, sp1.variablesGet(4));
    }

    @Test
    public void test1() {
        int cnt = 0;
        for (ShortestPath shortestPath : sps) {
            LOGGER.info("WeightedGraph {}", cnt);
            Map<Variable, LV> startAtX1 = shortestPath.links(x, null);
            assertEquals(4, startAtX1.size());
            assertEquals(v2, startAtX1.get(s));
            assertEquals(v0, startAtX1.get(x));
            assertEquals(v0, startAtX1.get(e));
            assertEquals(v0, startAtX1.get(se));
            assertNull(startAtX1.get(lt));
            cnt++;
        }
    }
}
