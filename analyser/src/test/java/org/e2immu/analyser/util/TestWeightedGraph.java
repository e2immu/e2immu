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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestWeightedGraph {

    @Test
    public void test1() {
        WeightedGraph<Character> graph = new WeightedGraph<>();

        graph.addNode('a', Map.of('b', 0, 'c', 1));
        graph.addNode('b', Map.of('d', 1, 'c', 0));
        graph.addNode('c', Map.of());

        Map<Character, Integer> fromA = graph.links('a');
        assertEquals(Map.of('a', 0, 'b', 0, 'c', 0, 'd', 1), fromA);

        Map<Character, Integer> fromD = graph.links('d');
        assertEquals(Map.of('d', 0), fromD);
    }

    @Test
    public void test2() {
        WeightedGraph<Character> graph = new WeightedGraph<>();

        graph.addNode('a', Map.of('b', 1));
        graph.addNode('b', Map.of('c', 0));
        graph.addNode('c', Map.of('d', 2));

        Map<Character, Integer> fromA = graph.links('a');
        assertEquals(Map.of('a', 0, 'b', 1, 'c', 1, 'd', 2), fromA);

        graph.addNode('d', Map.of('a', 1));

        // we now have a cycle a-1->b-0->c-2->d-1->a
        Map<Character, Integer> fromA2 = graph.links('a');
        assertEquals(Map.of('a', 0, 'b', 1, 'c', 1, 'd', 2), fromA2);

        Map<Character, Integer> fromB2 = graph.links('b');
        assertEquals(Map.of('a', 2, 'b', 0, 'c', 0, 'd', 2), fromB2);
    }

    @Test
    public void test1Bi() {
        WeightedGraph<Character> graph = new WeightedGraph<>();

        graph.addNode('a', Map.of('b', 0, 'c', 1), true);
        graph.addNode('b', Map.of('d', 1, 'c', 0), true);
        graph.addNode('c', Map.of(), true);

        Map<Character, Integer> fromA = graph.links('a');
        assertEquals(Map.of('a', 0, 'b', 0, 'c', 0, 'd', 1), fromA);

        Map<Character, Integer> fromD = graph.links('d');
        assertEquals(Map.of('a', 1, 'b', 1, 'c', 1, 'd', 0), fromD);
    }

    @Test
    public void test2Bi() {
        WeightedGraph<Character> graph = new WeightedGraph<>();

        graph.addNode('a', Map.of('b', 1), true);
        graph.addNode('b', Map.of('c', 0), true);
        graph.addNode('c', Map.of('d', 2), true);

        Map<Character, Integer> fromA = graph.links('a');
        assertEquals(Map.of('a', 0, 'b', 1, 'c', 1, 'd', 2), fromA);

        graph.addNode('d', Map.of('a', 1), true);

        // we now have a cycle a<-1->b<-0->c<-2->d<-1->a
        Map<Character, Integer> fromA2 = graph.links('a');
        assertEquals(Map.of('a', 0, 'b', 1, 'c', 1, 'd', 1), fromA2);

        Map<Character, Integer> fromB2 = graph.links('b');
        assertEquals(Map.of('a', 1, 'b', 0, 'c', 0, 'd', 1), fromB2);
    }
}
