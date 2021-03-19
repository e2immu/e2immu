/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
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
        List<Character> sorted = graph.sorted(NO_CYCLES);
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
        List<Character> sorted = graph.sorted(NO_CYCLES);
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
        List<Character> sorted = graph.sorted(NO_CYCLES);
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
        });
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
        List<Character> sorted = graph.sorted(c -> countCycles.incrementAndGet());
        assertEquals("[a, b, c, d]", sorted.toString());
        assertEquals(1, countCycles.get());

        assertFalse(graph.isFrozen());
        graph.ensureNotFrozen();
        try {
            graph.ensureFrozen();
            fail();
        } catch (UnsupportedOperationException e) {
            // normal behaviour
        }
        graph.freeze();
        try {
            graph.addNode('e', List.of());
            fail();
        } catch (UnsupportedOperationException e) {
            // normal behaviour
        }
        assertTrue(graph.isFrozen());
        graph.ensureFrozen();
        try {
            graph.ensureNotFrozen();
            fail();
        } catch (UnsupportedOperationException e) {
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
        });
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
        });
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
