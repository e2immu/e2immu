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
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.analyser.LV.*;
import static org.e2immu.analyser.analyser.LinkedVariables.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestWeightedGraph_1 extends CommonWG {

    Variable thisVar, toDo, nodeMap, cycle, smallerCycle, removed;
    WeightedGraph wg;
    ShortestPath shortestPath;

    /*
     thisVar 0 <----D----> removed 0
       ^ <--\
       |     --\
       |4       --\4
       v           --->
     cycle 0           smallerCycle 0
       ^ <--\
       |4   ---\
       |        ---\4
       v             --->
     nodeMap 0 <----4--->  to_do 0
     */
    @BeforeEach
    public void beforeEach() {
        thisVar = makeVariable("thisVar");
        toDo = makeVariable("toDo");
        nodeMap = makeVariable("nodeMap");
        cycle = makeVariable("cycle");
        smallerCycle = makeVariable("smallerCycle");
        removed = makeVariable("removed");

        wg = new WeightedGraphImpl();

        LV this_4_cycle = LV.createHC(LV.typeParameter(null, 0),
                LV.typeParameters(null, List.of(0), null, List.of(1, 0)));
        assertEquals("<0>-4-<0,1-0>", this_4_cycle.toString());

        LV map_4_map = LV.createHC(  LV.typeParameters(null, List.of(0), null, List.of(1, 0)),
                LV.typeParameters(null, List.of(0), null, List.of(1, 0)));
        assertEquals("<0,1-0>-4-<0,1-0>", map_4_map.toString());

        wg.addNode(thisVar, Map.of(thisVar, v0, removed, delay, cycle, this_4_cycle, smallerCycle, this_4_cycle));
        wg.addNode(removed, Map.of(removed, v0, thisVar, delay));
        wg.addNode(smallerCycle, Map.of(smallerCycle, v0, thisVar, this_4_cycle.reverse()));
        wg.addNode(cycle, Map.of(cycle, v0, nodeMap, map_4_map, toDo, map_4_map, thisVar, this_4_cycle.reverse()));
        wg.addNode(nodeMap, Map.of(nodeMap, v0, toDo, map_4_map, cycle, map_4_map));
        wg.addNode(toDo, Map.of(toDo, v0, nodeMap, map_4_map, cycle, map_4_map));
        shortestPath = wg.shortestPath();
    }

    @Test @DisplayName("start at 'toDo', limit dependent")
    public void test1() {
        Map<Variable, LV> startAtToDo = shortestPath.links(toDo, LINK_DEPENDENT);
        assertEquals(1, startAtToDo.size());
        assertEquals(v0, startAtToDo.get(toDo));
        assertNull(startAtToDo.get(cycle));
        assertNull(startAtToDo.get(nodeMap));
    }


    @Test @DisplayName("start at 'toDo', no limit")
    public void test2() {
        Map<Variable, LV> startAtToDo = shortestPath.links(toDo, null);
        assertEquals(6, startAtToDo.size());
        assertEquals(v0, startAtToDo.get(toDo));
        assertEquals(LINK_COMMON_HC, startAtToDo.get(cycle));
        assertEquals(LINK_COMMON_HC, startAtToDo.get(nodeMap));
        assertEquals(LINK_COMMON_HC, startAtToDo.get(smallerCycle));
        assertEquals(LINK_COMMON_HC, startAtToDo.get(thisVar));
        assertEquals(delay, startAtToDo.get(removed));
    }


    @Test @DisplayName("start at 'removed', no limit")
    public void test3() {
        Map<Variable, LV> startAtRemoved = shortestPath.links(removed, null);
        assertEquals(6, startAtRemoved.size());
        assertEquals(delay, startAtRemoved.get(thisVar));
        assertEquals(LINK_STATICALLY_ASSIGNED, startAtRemoved.get(removed));
        assertEquals(delay, startAtRemoved.get(cycle));
        assertEquals(delay, startAtRemoved.get(smallerCycle));
        assertEquals(delay, startAtRemoved.get(toDo));
        assertEquals(delay, startAtRemoved.get(nodeMap));
    }

    @Test @DisplayName("start at 'removed', limit dependent")
    public void test4() {
        Map<Variable, LV> startAtRemoved = shortestPath.links(removed, LINK_DEPENDENT);
        assertEquals(2, startAtRemoved.size());
        assertNull(startAtRemoved.get(cycle));
        assertNull(startAtRemoved.get(smallerCycle));
        assertEquals(delay, startAtRemoved.get(thisVar));
        assertEquals(LINK_STATICALLY_ASSIGNED, startAtRemoved.get(removed));
    }

}
