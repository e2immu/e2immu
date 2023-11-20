package org.e2immu.analyser.util.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestLinearize {

    @Test
    public void test1() {
        Map<String, Map<String, Long>> initialGraph = Map.of(
                "v1", Map.of("v2", 1L, "v3", 1L),
                "v2", Map.of(),
                "v3", Map.of("v4", 1L),
                "v4", Map.of());
        G<String> g = G.create(initialGraph);
        assertEquals("[v1]->1->[v2], [v1]->1->[v3], [v3]->1->[v4]", g.toString());
        GraphOperations.LinearizationResult<String> r = GraphOperations.linearize(g);
        assertEquals("L=[[v2], [v4]]; [[v3]]; [[v1]] P= R=", r.toString());
        assertEquals(3, r.linearized().size());
        assertEquals(0, r.quality());
    }

    @Test
    public void test1b() {
        Map<String, Map<String, Long>> initialGraph = Map.of(
                "v1", Map.of("v2", 1L),
                "v2", Map.of("v3", 1L),
                "v3", Map.of("v4", 1L),
                "v4", Map.of());
        G<String> g = G.create(initialGraph);
        assertEquals("[v1]->1->[v2], [v2]->1->[v3], [v3]->1->[v4]", g.toString());
        GraphOperations.LinearizationResult<String> r = GraphOperations.linearize(g);
        assertEquals("L=[[v4]]; [[v3]]; [[v2]]; [[v1]] P= R=", r.toString());
        assertEquals(4, r.linearized().size());
        assertEquals(0, r.quality());
    }

    @Test
    public void test1c() {
        Map<String, Map<String, Long>> initialGraph = Map.of(
                "v4", Map.of(),
                "v3", Map.of("v4", 1L),
                "v2", Map.of("v3", 1L),
                "v1", Map.of("v2", 1L));
        G<String> g = G.create(initialGraph);
        assertEquals("[v1]->1->[v2], [v2]->1->[v3], [v3]->1->[v4]", g.toString());
        GraphOperations.LinearizationResult<String> r = GraphOperations.linearize(g);
        assertEquals("L=[[v4]]; [[v3]]; [[v2]]; [[v1]] P= R=", r.toString());
        assertEquals(4, r.linearized().size());
        assertEquals(0, r.quality());
    }

    @Test
    public void test2() {
        Map<String, Map<String, Long>> initialGraph = Map.of(
                "v1", Map.of("v3", 1L),
                "v2", Map.of("v3", 2L),
                "v3", Map.of("v4", 3L),
                "v4", Map.of("v7", 4L),
                "v5", Map.of("v4", 5L),
                "v6", Map.of("v5", 6L),
                "v7", Map.of("v6", 7L, "v8", 8L),
                "v8", Map.of(),
                "v9", Map.of("v10", 9L),
                "v10", Map.of("v9", 10L));
        G<String> g = G.create(initialGraph);
        assertEquals("""
                [v10]->10->[v9], [v1]->1->[v3], [v2]->2->[v3], [v3]->3->[v4], [v4]->4->[v7], [v5]->5->[v4], \
                [v6]->6->[v5], [v7]->7->[v6], [v7]->8->[v8], [v9]->9->[v10]\
                """, g.toString());
        GraphOperations.LinearizationResult<String> r = GraphOperations.linearize(g);
        assertEquals("L=[[v8]] P=[v1], [v2], [v3] R=[[v4], [v5], [v6], [v7]]; [[v10], [v9]]", r.toString());
        assertEquals(4, r.quality());
        V<String> v5 = g.vertex("v5");
        V<String> v6 = g.vertex("v6");
        V<String> v9 = g.vertex("v9");
        V<String> v10 = g.vertex("v10");

        BreakCycles<String> bc = new BreakCycles<>((g1, cycle) -> new BreakCycles.Action<String>() {
            @Override
            public G<String> apply() {
                if (cycle.contains(v6)) {
                    return g1.subGraph(cycle).withFewerEdges(Map.of(v6, Set.of(v5)));
                }
                if (cycle.contains(v9)) {
                    return g1.subGraph(cycle).withFewerEdges(Map.of(v9, Set.of(v10)));
                }
                throw new UnsupportedOperationException();
            }

            @Override
            public BreakCycles.ActionInfo<String> info() {
                if (cycle.contains(v6)) {
                    return new BreakCycles.EdgeRemoval<>(Map.of(v6, Map.of(v5, 1L)));
                }
                if (cycle.contains(v9)) {
                    return new BreakCycles.EdgeRemoval<>(Map.of(v9, Map.of(v10, 1L)));
                }
                throw new UnsupportedOperationException();
            }
        });
        BreakCycles.Linearization<String> linearization = bc.go(g);
        assertEquals("[v8]; [v6, v9]; [v10, v7]; [v4]; [v3, v5]; [v1]; [v2]", linearization.toString());

        BreakCycles<String> bc2 = new BreakCycles<String>((g1, cycle) -> {
            if (cycle.contains(v6)) {
                return new BreakCycles.Action<>() {
                    @Override
                    public G<String> apply() {
                        return g1.subGraph(cycle).withFewerEdges(Map.of(v6, Set.of(v5)));
                    }

                    @Override
                    public BreakCycles.ActionInfo<String> info() {
                        return new BreakCycles.EdgeRemoval<>(Map.of(v6, Map.of(v5, 1L)));
                    }
                };
            }
            if (cycle.contains(v9)) {
                return null; // we cannot break it
            }
            throw new UnsupportedOperationException();
        });
        BreakCycles.Linearization<String> linearization2 = bc2.go(g);
        assertEquals("[v8]; [v10, v9]; [v6]; [v7]; [v4]; [v3, v5]; [v1]; [v2]", linearization2.toString());

        BreakCycles<String> bc3 = new BreakCycles<>(new BreakCycles.GreedyEdgeRemoval<>());
        BreakCycles.Linearization<String> linearization3 = bc3.go(g);
        // because all the edges have a different weight, we'll always get the same result!
        assertEquals("[v8]; [v4, v9]; [v10, v3, v5]; [v1, v2, v6]; [v7]", linearization3.toString());
    }

    // removal of a single edge; but not the one with the lowest ranking
    @Test
    public void test3() {
        Map<String, Map<String, Long>> initialGraph = Map.of(
                "v1", Map.of("v2", 1L, "v3", 2L),
                "v2", Map.of("v3", 3L),
                "v3", Map.of("v4", 6L),
                "v4", Map.of("v5", 4L),
                "v5", Map.of("v1", 5L, "v3", 5L));

        G<String> g = G.create(initialGraph);
        assertEquals("""
                [v1]->1->[v2], [v1]->2->[v3], [v2]->3->[v3], [v3]->6->[v4], [v4]->4->[v5], [v5]->5->[v1], [v5]->5->[v3]\
                """, g.toString());
        BreakCycles<String> bc = new BreakCycles<>(new BreakCycles.GreedyEdgeRemoval<>());
        BreakCycles.Linearization<String> linearization = bc.go(g);
        // because all the edges have a different weight, we'll always get the same result!
        assertEquals("[v4]; [v3]; [v2]; [v1]; [v5]", linearization.toString());
        assertEquals(1, linearization.actionLog().size());
        assertEquals("EdgeRemoval[edges={[v4]={[v5]=4}}]", linearization.actionLog().get(0).toString());
    }

    // remove two cycles
    @Test
    public void test4() {
        Map<String, Map<String, Long>> initialGraph = Map.of(
                "v1", Map.of("v2", 1L),
                "v2", Map.of("v3", 3L),
                "v3", Map.of("v4", 6L, "v1", 2L),
                "v4", Map.of("v5", 4L),
                "v5", Map.of("v1", 5L, "v3", 5L));

        G<String> g = G.create(initialGraph);
        assertEquals("""
                [v1]->1->[v2], [v2]->3->[v3], [v3]->2->[v1], [v3]->6->[v4], [v4]->4->[v5], [v5]->5->[v1], [v5]->5->[v3]\
                """, g.toString());
        BreakCycles<String> bc = new BreakCycles<>(new BreakCycles.GreedyEdgeRemoval<>());
        BreakCycles.Linearization<String> linearization = bc.go(g);
        // because all the edges have a different weight, we'll always get the same result!
        assertEquals("[v1]; [v4]; [v3]; [v2, v5]", linearization.toString());
        assertEquals(2, linearization.actionLog().size());
        assertEquals("[EdgeRemoval[edges={[v1]={[v2]=1}}], EdgeRemoval[edges={[v4]={[v5]=4}}]]",
                linearization.actionLog().toString());
    }
}
