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

import com.sun.source.tree.Tree;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

public class TestDependencyGraph {

    @Test
    public void test() {
        DependencyGraph<Character> graph = new DependencyGraph<>();

        graph.addNode('a', List.of());
        graph.addNode('b', List.of());
        graph.addNode('c', List.of('a', 'b'));
        List<Character> sorted = graph.sorted();
        Assert.assertEquals("[a, b, c]", sorted.toString());

        Assert.assertEquals("[a]", graph.dependencies('a').toString());
        Assert.assertEquals("[b]", graph.dependencies('b').toString());
        Assert.assertEquals("[a, b]", graph.dependencies('c').toString());
    }

    @Test
    public void test2() {
        DependencyGraph<Character> graph = new DependencyGraph<>();

        graph.addNode('a', List.of());
        graph.addNode('b', List.of('c'));
        graph.addNode('c', List.of('a'));
        List<Character> sorted = graph.sorted();
        Assert.assertEquals("[a, c, b]", sorted.toString());

        Assert.assertEquals("[a]", graph.dependencies('a').toString());
        Assert.assertEquals("[a]", graph.dependencies('b').toString());
        Assert.assertEquals("[a]", graph.dependencies('c').toString());
    }

    @Test
    public void test3() {
        DependencyGraph<Character> graph = new DependencyGraph<>();

        graph.addNode('a', List.of('c'));
        graph.addNode('b', List.of('c'));
        graph.addNode('c', List.of());
        List<Character> sorted = graph.sorted();
        Assert.assertEquals("[c, a, b]", sorted.toString());

        Assert.assertEquals("[c]", graph.dependencies('a').toString());
        Assert.assertEquals("[c]", graph.dependencies('b').toString());
        Assert.assertEquals("[c]", graph.dependencies('c').toString());
    }

    @Test
    public void test4() {
        DependencyGraph<Character> graph = new DependencyGraph<>();
        Assert.assertTrue(graph.isEmpty());

        graph.addNode('a', List.of('c'));
        graph.addNode('b', List.of('c'));
        graph.addNode('c', List.of('a'));
        List<Character> sorted = graph.sorted();
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

        graph.addNode('a', List.of());
        graph.addNode('b', List.of('d'));
        graph.addNode('c', List.of('a', 'b'));
        graph.addNode('d', List.of('a', 'b', 'c'));
        List<Character> sorted = graph.sorted();
        Assert.assertEquals("[a, b, c, d]", sorted.toString());

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
    public void test6() {
        DependencyGraph<Character> graph = new DependencyGraph<>();

        // c -> b -> a
        graph.addNode('a', List.of());
        graph.addNode('b', List.of('a'));
        graph.addNode('c', List.of('b'));

        Assert.assertEquals("[a]", graph.dependencies('c').toString());
        Assert.assertEquals("[a]", graph.dependencies('b').toString());

        DependencyGraph<Character> graph2 = new DependencyGraph<>();

        // c -> b -> a
        graph2.addNode('a', List.of());
        graph2.addNode('b', List.of('a'));

        Assert.assertFalse(graph.equalTransitiveTerminals(graph2));
        graph2.addNode('c', List.of('b'));
        Assert.assertTrue(graph.equalTransitiveTerminals(graph2));

        graph.addNode('d', List.of('b'));
        Assert.assertFalse(graph.equalTransitiveTerminals(graph2));
        graph2.addNode('d', List.of('b'));
        Assert.assertTrue(graph.equalTransitiveTerminals(graph2));

        graph.addNode('e', List.of('b'));
        Assert.assertFalse(graph.equalTransitiveTerminals(graph2));
        System.out.println("Deps of 'e': " + graph.dependencies('e'));
        graph2.addNode('e', List.of('a'));
        System.out.println("Deps of 'e': " + graph2.dependencies('e'));
        Assert.assertTrue(graph.equalTransitiveTerminals(graph2));

        graph.addNode('f', List.of('e'));
        graph.addNode('f', List.of());
        Assert.assertFalse(graph.equalTransitiveTerminals(graph2));
        System.out.println("Deps of 'f': " + graph.dependencies('f'));
        graph2.addNode('g', List.of());
        graph2.addNode('f', List.of('g'));
        System.out.println("Deps of 'f': " + graph2.dependencies('f'));
        Assert.assertFalse(graph.equalTransitiveTerminals(graph2));
    }
}
