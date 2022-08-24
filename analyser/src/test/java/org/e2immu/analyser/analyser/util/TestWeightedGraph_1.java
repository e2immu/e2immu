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

import java.util.Map;

import static org.e2immu.analyser.analyser.LinkedVariables.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestWeightedGraph_1 {

    Variable thisVar, toDo, nodeMap, cycle, smallerCycle, removed;
    CausesOfDelay delay;
    final DV v0 = LINK_STATICALLY_ASSIGNED;
    final DV v3 = LINK_INDEPENDENT_HC;
    WeightedGraph wg;

    @BeforeEach
    public void beforeEach() {
        thisVar = makeVariable("thisVar");
        toDo = makeVariable("toDo");
        nodeMap = makeVariable("nodeMap");
        cycle = makeVariable("cycle");
        smallerCycle = makeVariable("smallerCycle");
        removed = makeVariable("removed");
        delay = DelayFactory.createDelay(new SimpleCause(Location.NOT_YET_SET, CauseOfDelay.Cause.ECI));

        wg = new WeightedGraph();
        wg.addNode(thisVar, Map.of(thisVar, v0, removed, delay, cycle, v3, smallerCycle, v3));
        wg.addNode(removed, Map.of(removed, v0, thisVar, delay));
        wg.addNode(smallerCycle, Map.of(thisVar, v3));
        wg.addNode(cycle, Map.of(cycle, v0, nodeMap, v3, toDo, v3, thisVar, v3));
        wg.addNode(nodeMap, Map.of(nodeMap, v0, toDo, v3, cycle, v3));
        wg.addNode(toDo, Map.of(toDo, v0, nodeMap, v3, cycle, v3));
    }

    @Test
    public void test1() {
        Map<Variable, DV> startAtToDo = wg.links(toDo, LINK_STATICALLY_ASSIGNED, false);
        assertEquals(1, startAtToDo.size());
        assertEquals(v0, startAtToDo.get(toDo));
     //   assertEquals(DV.MAX_INT_DV, startAtToDo.get(cycle));
      //  assertEquals(DV.MAX_INT_DV, startAtToDo.get(nodeMap));
    }

    @Test
    public void test1b() {
        Map<Variable, DV> startAtToDo = wg.links(toDo, LINK_DEPENDENT, true);
        assertEquals(1, startAtToDo.size());
        assertEquals(v0, startAtToDo.get(toDo));
      //  assertEquals(DV.MAX_INT_DV, startAtToDo.get(cycle));
    //    assertEquals(DV.MAX_INT_DV, startAtToDo.get(nodeMap));
    }

    @Test
    public void test2() {
        Map<Variable, DV> startAtToDo = wg.links(toDo, LINK_INDEPENDENT_HC, false);
        assertEquals(5, startAtToDo.size());
        assertEquals(v0, startAtToDo.get(toDo));
        assertEquals(v3, startAtToDo.get(cycle));
        assertEquals(v3, startAtToDo.get(nodeMap));
        assertEquals(v3, startAtToDo.get(thisVar));
        assertEquals(v3, startAtToDo.get(smallerCycle));
    }

    @Test
    public void test3() {
        Map<Variable, DV> startAtToDo = wg.links(toDo, LINK_INDEPENDENT_HC, true);
        assertEquals(6, startAtToDo.size());
        assertEquals(v0, startAtToDo.get(toDo));
        assertEquals(delay, startAtToDo.get(cycle));
        assertEquals(delay, startAtToDo.get(nodeMap));
        assertEquals(delay, startAtToDo.get(smallerCycle));
        assertEquals(delay, startAtToDo.get(thisVar));
        assertEquals(delay, startAtToDo.get(removed));
    }


    @Test
    public void test4() {
        Map<Variable, DV> startAtRemoved = wg.links(removed, LINK_INDEPENDENT_HC, true);
        assertEquals(6, startAtRemoved.size());
        assertEquals(delay, startAtRemoved.get(thisVar));
        assertEquals(LINK_STATICALLY_ASSIGNED, startAtRemoved.get(removed));
        assertEquals(delay, startAtRemoved.get(cycle));
        assertEquals(delay, startAtRemoved.get(smallerCycle));
        assertEquals(delay, startAtRemoved.get(toDo));
        assertEquals(delay, startAtRemoved.get(nodeMap));
    }

    @Test
    public void test4b() {
        Map<Variable, DV> startAtRemoved = wg.links(removed, LINK_DEPENDENT, true);
        assertEquals(2, startAtRemoved.size());
      //  assertEquals(DV.MAX_INT_DV, startAtRemoved.get(cycle));
      //  assertEquals(DV.MAX_INT_DV, startAtRemoved.get(smallerCycle));
        assertEquals(delay, startAtRemoved.get(thisVar));
        assertEquals(LINK_STATICALLY_ASSIGNED, startAtRemoved.get(removed));
    }

    private Variable makeVariable(String name) {
        TypeInfo t = new TypeInfo("a.b.c", "T");
        return new LocalVariableReference(new LocalVariable(name, new ParameterizedType(t, 0)));
    }
}
