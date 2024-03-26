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

import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.e2immu.analyser.analyser.LV.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestWeightedGraph_0 extends CommonWG {

    Variable thisVar, cycle, removed;
    WeightedGraph wg;
    ShortestPath shortestPath;

    /*
     thisVar 0 <----D----> removed 0
       ^
       |
       |4
       v
     cycle 0
     */
    @BeforeEach
    public void beforeEach() {
        thisVar = makeVariable("thisVar"); // <T>
        cycle = makeVariable("cycle"); // <T, Node<T>>
        removed = makeVariable("removed"); // <T>, but delayed

        wg = new WeightedGraphImpl();

        LV this_4_cycle = LV.createHC(HiddenContentSelector.CsSet.selectTypeParameter(0),
                HiddenContentSelector.CsSet.selectTypeParameter(0));
        assertEquals("<0>-4-<0>", this_4_cycle.toString());

        wg.addNode(thisVar, Map.of(thisVar, v0, removed, delay, cycle, this_4_cycle));
        wg.addNode(cycle, Map.of(cycle, v0, thisVar, this_4_cycle.reverse()));
        wg.addNode(removed, Map.of(removed, v0, thisVar, delay));
        shortestPath = wg.shortestPath();
    }

    @Test
    @DisplayName("start at 'cycle', limit dependent")
    public void test1() {
        Map<Variable, LV> startAt = shortestPath.links(cycle, LINK_DEPENDENT);
        assertEquals(1, startAt.size());
        assertEquals(v0, startAt.get(cycle));
        assertNull(startAt.get(thisVar));
        assertNull(startAt.get(removed));
    }


    @Test
    @DisplayName("start at 'toDo', no limit")
    public void test2() {
        Map<Variable, LV> startAt = shortestPath.links(cycle, null);
        assertEquals(3, startAt.size());
        assertEquals(v0, startAt.get(cycle));
        assertEquals(v4, startAt.get(thisVar));
        assertEquals(delay, startAt.get(removed));
    }


    @Test
    @DisplayName("start at 'removed', no limit")
    public void test3() {
        Map<Variable, LV> startAt = shortestPath.links(removed, null);
        assertEquals(3, startAt.size());
        assertEquals(v0, startAt.get(removed));
        assertEquals(delay, startAt.get(thisVar));
        assertEquals(delay, startAt.get(cycle));

    }

    @Test
    @DisplayName("start at 'removed', limit dependent")
    public void test4() {
        Map<Variable, LV> startAt = shortestPath.links(removed, LINK_DEPENDENT);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(removed));
        assertEquals(delay, startAt.get(thisVar));
        assertNull(startAt.get(cycle));
    }

}
