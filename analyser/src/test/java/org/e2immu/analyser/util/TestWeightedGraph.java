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

package org.e2immu.analyser.util;

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Location;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestWeightedGraph {

    private static final DV zero = new DV.NoDelay(0);
    private static final DV one = new DV.NoDelay(1);
    private static final DV two = new DV.NoDelay(2);

    private static final DV delay = new DV.SingleDelay(Location.NOT_YET_SET, CauseOfDelay.Cause.INITIAL_VALUE);
    
    @Test
    public void test1() {
        WeightedGraph<Character, DV> graph = new WeightedGraph<>(() -> zero);

        graph.addNode('a', Map.of('b', zero, 'c', one));
        graph.addNode('b', Map.of('d', one, 'c', zero));
        graph.addNode('c', Map.of());

        Map<Character, DV> fromA = graph.links('a', true);
        assertEquals(Map.of('a', zero, 'b', zero, 'c', zero, 'd', one), fromA);

        Map<Character, DV> fromD = graph.links('d', true);
        assertEquals(Map.of('d', zero), fromD);
    }

    @Test
    public void test1Delay() {
        WeightedGraph<Character, DV> graph = new WeightedGraph<>(() -> zero);

        graph.addNode('a', Map.of('b', delay, 'c', one));
        graph.addNode('b', Map.of('d', one, 'c', zero));
        graph.addNode('c', Map.of());

        Map<Character, DV> fromA = graph.links('a', false);
        assertEquals(Map.of('a', zero, 'c', one), fromA);

        Map<Character, DV> fromAFollow = graph.links('a', true);
        assertEquals(Map.of('a', zero, 'b', delay, 'c', delay, 'd', delay), fromAFollow);
    }


    @Test
    public void test2() {
        WeightedGraph<Character, DV> graph = new WeightedGraph<>(() -> zero);

        graph.addNode('a', Map.of('b', one));
        graph.addNode('b', Map.of('c', zero));
        graph.addNode('c', Map.of('d', two));

        Map<Character, DV> fromA = graph.links('a', true);
        assertEquals(Map.of('a', zero, 'b', one, 'c', one, 'd', two), fromA);

        graph.addNode('d', Map.of('a', one));

        // we now have a cycle a-1->b-0->c-2->d-1->a
        Map<Character, DV> fromA2 = graph.links('a', true);
        assertEquals(Map.of('a', zero, 'b', one, 'c', one, 'd', two), fromA2);

        Map<Character, DV> fromB2 = graph.links('b', true);
        assertEquals(Map.of('a', two, 'b', zero, 'c', zero, 'd', two), fromB2);
    }

    @Test
    public void test1Bi() {
        WeightedGraph<Character, DV> graph = new WeightedGraph<>(() -> zero);

        graph.addNode('a', Map.of('b', zero, 'c', one), true);
        graph.addNode('b', Map.of('d', one, 'c', zero), true);
        graph.addNode('c', Map.of(), true);

        Map<Character, DV> fromA = graph.links('a', true);
        assertEquals(Map.of('a', zero, 'b', zero, 'c', zero, 'd', one), fromA);

        Map<Character, DV> fromD = graph.links('d', true);
        assertEquals(Map.of('a', one, 'b', one, 'c', one, 'd', zero), fromD);
    }

    @Test
    public void test2Bi() {
        WeightedGraph<Character, DV> graph = new WeightedGraph<>(() -> zero);

        graph.addNode('a', Map.of('b', one), true);
        graph.addNode('b', Map.of('c', zero), true);
        graph.addNode('c', Map.of('d', two), true);

        Map<Character, DV> fromA = graph.links('a', true);
        assertEquals(Map.of('a', zero, 'b', one, 'c', one, 'd', two), fromA);

        graph.addNode('d', Map.of('a', one), true);

        // we now have a cycle a<-1->b<-0->c<-2->d<-1->a
        Map<Character, DV> fromA2 = graph.links('a', true);
        assertEquals(Map.of('a', zero, 'b', one, 'c', one, 'd', one), fromA2);

        Map<Character, DV> fromB2 = graph.links('b', true);
        assertEquals(Map.of('a', one, 'b', zero, 'c', zero, 'd', one), fromB2);
    }

    @Test
    public void testDelay() {
        WeightedGraph<Character, DV> graph = new WeightedGraph<>(() -> zero);

        graph.addNode('a', Map.of('b', zero, 'c', delay), true);

        Map<Character, DV> fromB = graph.links('b', true);
        assertEquals(Map.of('a', zero, 'b', zero, 'c', delay), fromB);

        Map<Character, DV> fromC = graph.links('c', true);
        assertEquals(Map.of('a', delay, 'b', delay, 'c', zero), fromC);
    }
}
