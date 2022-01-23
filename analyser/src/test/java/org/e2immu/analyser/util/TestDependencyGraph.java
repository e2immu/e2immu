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

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class TestDependencyGraph {

    private static final Consumer<Character> NO_CYCLES = c -> {
        throw new UnsupportedOperationException(c + " participates in cycle");
    };

    @Test
    public void test() {
        DependencyGraph<Character> graph = new DependencyGraph<>();
        // c -> a, c -> b
        graph.addNode('a', List.of());
        graph.addNode('b', List.of());
        graph.addNode('c', List.of('a', 'b'));
        List<Character> sorted = graph.sorted(NO_CYCLES, null, null);
        assertEquals("[a, b, c]", sorted.toString());

        assertEquals("[a]", graph.dependencies('a').toString());
        assertEquals("[b]", graph.dependencies('b').toString());
        assertEquals("[a, b, c]", graph.dependencies('c').toString());
    }

    @Test
    public void test2() {
        DependencyGraph<Character> graph = new DependencyGraph<>();
        // b -> c -> a
        graph.addNode('a', List.of());
        graph.addNode('b', List.of('c'));
        graph.addNode('c', List.of('a'));
        List<Character> sorted = graph.sorted(NO_CYCLES, null, null);
        assertEquals("[a, c, b]", sorted.toString());

        assertEquals("[a]", graph.dependencies('a').toString());
        assertEquals("[a, b, c]", graph.dependencies('b').toString());
        assertEquals("[a, c]", graph.dependencies('c').toString());
    }

    @Test
    public void test3() {
        DependencyGraph<Character> graph = new DependencyGraph<>();
        // a -> c, b -> c
        graph.addNode('a', List.of('c'));
        graph.addNode('b', List.of('c'));
        graph.addNode('c', List.of());
        List<Character> sorted = graph.sorted(NO_CYCLES, null, null);
        assertEquals("[c, a, b]", sorted.toString());

        assertEquals("[a, c]", graph.dependencies('a').toString());
        assertEquals("[b, c]", graph.dependencies('b').toString());
        assertEquals("[c]", graph.dependencies('c').toString());
    }

    @Test
    public void test4() {
        // cycle: a<->c, b->c
        DependencyGraph<Character> graph = new DependencyGraph<>();
        assertTrue(graph.isEmpty());

        graph.addNode('a', List.of('c'));
        graph.addNode('b', List.of('c'));
        graph.addNode('c', List.of('a'));
        List<Character> sorted = graph.sorted(c -> {
            if ('b' == c) throw new UnsupportedOperationException();
        }, null, null);
        assertEquals("[a, c, b]", sorted.toString());

        Set<Character> elements = new TreeSet<>();
        graph.visit((c, list) -> elements.add(c));
        assertEquals("[a, b, c]", elements.toString());

        assertEquals(3, graph.size());
        assertFalse(graph.isEmpty());
        assertEquals(3, graph.relations());
    }

    @Test
    public void test5() {
        DependencyGraph<Character> graph = new DependencyGraph<>();

        // b -> d, c -> a, b, d -> a, b, c
        graph.addNode('a', List.of());
        graph.addNode('b', List.of('d'));
        graph.addNode('c', List.of('a', 'b'));
        graph.addNode('d', List.of('a', 'b', 'c'));
        AtomicInteger countCycles = new AtomicInteger();
        List<Character> sorted = graph.sorted(c -> countCycles.incrementAndGet(), null, null);
        assertEquals("[a, b, c, d]", sorted.toString());
        assertEquals(1, countCycles.get());

        assertFalse(graph.isFrozen());
        graph.ensureNotFrozen();
        try {
            graph.ensureFrozen();
            fail();
        } catch (IllegalStateException e) {
            // normal behaviour
        }
        graph.freeze();
        try {
            graph.addNode('e', List.of());
            fail();
        } catch (IllegalStateException e) {
            // normal behaviour
        }
        assertTrue(graph.isFrozen());
        graph.ensureFrozen();
        try {
            graph.ensureNotFrozen();
            fail();
        } catch (IllegalStateException e) {
            // normal behaviour
        }
    }

    @Test
    public void test7Circular() {
        DependencyGraph<Character> graph = new DependencyGraph<>();

        // d -> c -> b -> a -> d, a -> e
        graph.addNode('a', List.of('d', 'e'));
        graph.addNode('b', List.of('a'));
        graph.addNode('c', List.of('b'));
        graph.addNode('d', List.of('c'));
        graph.addNode('e', List.of());

        for (char c : new char[]{'a', 'b', 'c', 'd'}) {
            assertEquals("[a, b, c, d, e]", graph.dependencies(c).toString());
        }
        assertEquals("[e]", graph.dependencies('e').toString());

        AtomicInteger countCycles = new AtomicInteger();
        List<Character> sorted = graph.sorted(c -> {
            System.out.println("Breaking a cycle with " + c);
            countCycles.incrementAndGet();
        }, null, null);
        assertEquals('e', (int) sorted.get(0));
        assertEquals(1, countCycles.get());
    }

    @Test
    public void test8Undefined() {
        DependencyGraph<Character> graph = new DependencyGraph<>();

        graph.addNode('a', List.of('a', 'b'));
        // we have NOT added b -> {}

        // the result is a cycle, because b -> {} does not exist
        AtomicInteger countCycles = new AtomicInteger();
        List<Character> sorted = graph.sorted(c -> {
            System.out.println("Breaking a cycle with " + c);
            countCycles.incrementAndGet();
        }, null, null);
        assertEquals("[a]", sorted.toString());
        assertEquals("[a, b]", graph.dependencies('a').toString());
        assertEquals(1, countCycles.get());
    }

    @Test
    public void test9Bidirectional() {
        DependencyGraph<Character> graph = new DependencyGraph<>();

        graph.addNode('a', List.of('b', 'c'), true);

        final String ALL = "[a, b, c]";
        assertEquals(ALL, graph.dependencies('a').toString());
        assertEquals(ALL, graph.dependencies('b').toString());
        assertEquals(ALL, graph.dependencies('c').toString());
    }
}
