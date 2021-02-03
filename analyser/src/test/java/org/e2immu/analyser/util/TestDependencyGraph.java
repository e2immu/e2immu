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

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
        Assert.assertEquals("[a, b, c]", sorted.toString());

        Assert.assertEquals("[a]", graph.dependencies('a').toString());
        Assert.assertEquals("[b]", graph.dependencies('b').toString());
        Assert.assertEquals("[a, b, c]", graph.dependencies('c').toString());
    }

    @Test
    public void test2() {
        DependencyGraph<Character> graph = new DependencyGraph<>();
        // b -> c -> a
        graph.addNode('a', List.of());
        graph.addNode('b', List.of('c'));
        graph.addNode('c', List.of('a'));
        List<Character> sorted = graph.sorted(NO_CYCLES);
        Assert.assertEquals("[a, c, b]", sorted.toString());

        Assert.assertEquals("[a]", graph.dependencies('a').toString());
        Assert.assertEquals("[a, b, c]", graph.dependencies('b').toString());
        Assert.assertEquals("[a, c]", graph.dependencies('c').toString());
    }

    @Test
    public void test3() {
        DependencyGraph<Character> graph = new DependencyGraph<>();
        // a -> c, b -> c
        graph.addNode('a', List.of('c'));
        graph.addNode('b', List.of('c'));
        graph.addNode('c', List.of());
        List<Character> sorted = graph.sorted(NO_CYCLES);
        Assert.assertEquals("[c, a, b]", sorted.toString());

        Assert.assertEquals("[a, c]", graph.dependencies('a').toString());
        Assert.assertEquals("[b, c]", graph.dependencies('b').toString());
        Assert.assertEquals("[c]", graph.dependencies('c').toString());
    }

    @Test
    public void test4() {
        // cycle: a<->c, b->c
        DependencyGraph<Character> graph = new DependencyGraph<>();
        Assert.assertTrue(graph.isEmpty());

        graph.addNode('a', List.of('c'));
        graph.addNode('b', List.of('c'));
        graph.addNode('c', List.of('a'));
        List<Character> sorted = graph.sorted(c -> {
            if ('b' == c) throw new UnsupportedOperationException();
        });
        Assert.assertEquals("[a, c, b]", sorted.toString());

        Set<Character> elements = new TreeSet<>();
        graph.visit((c, list) -> elements.add(c));
        Assert.assertEquals("[a, b, c]", elements.toString());

        Assert.assertEquals(3, graph.size());
        Assert.assertFalse(graph.isEmpty());
        Assert.assertEquals(3, graph.relations());
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
        Assert.assertEquals("[a, b, c, d]", sorted.toString());
        Assert.assertEquals(1, countCycles.get());

        Assert.assertFalse(graph.isFrozen());
        graph.ensureNotFrozen();
        try {
            graph.ensureFrozen();
            Assert.fail();
        } catch (UnsupportedOperationException e) {
            // normal behaviour
        }
        graph.freeze();
        try {
            graph.addNode('e', List.of());
            Assert.fail();
        } catch (UnsupportedOperationException e) {
            // normal behaviour
        }
        Assert.assertTrue(graph.isFrozen());
        graph.ensureFrozen();
        try {
            graph.ensureNotFrozen();
            Assert.fail();
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
            Assert.assertEquals("[a, b, c, d, e]", graph.dependencies(c).toString());
        }
        Assert.assertEquals("[e]", graph.dependencies('e').toString());

        AtomicInteger countCycles = new AtomicInteger();
        List<Character> sorted = graph.sorted(c -> {
            System.out.println("Breaking a cycle with " + c);
            countCycles.incrementAndGet();
        });
        Assert.assertEquals('e', (int) sorted.get(0));
        Assert.assertEquals(1, countCycles.get());
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
        Assert.assertEquals("[a]", sorted.toString());
        Assert.assertEquals("[a, b]", graph.dependencies('a').toString());
        Assert.assertEquals(1, countCycles.get());
    }
}
