package org.e2immu.analyser.util.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;

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
    }

    @Test
    public void test2() {
        Map<String, Map<String, Long>> initialGraph = Map.of(
                "v1", Map.of("v3", 1L),
                "v2", Map.of("v3", 1L),
                "v3", Map.of("v4", 1L),
                "v4", Map.of("v7", 1L),
                "v5", Map.of("v4", 1L),
                "v6", Map.of("v5", 1L),
                "v7", Map.of("v6", 1L, "v8", 1L),
                "v8", Map.of(),
                "v9", Map.of("v10", 1L),
                "v10", Map.of("v9", 1L));
        G<String> g = G.create(initialGraph);
        assertEquals("[v10]->1->[v9], [v1]->1->[v3], [v2]->1->[v3], [v3]->1->[v4], [v4]->1->[v7], [v5]->1->[v4], [v6]->1->[v5], [v7]->1->[v6], [v7]->1->[v8], [v9]->1->[v10]", g.toString());
        GraphOperations.LinearizationResult<String> r = GraphOperations.linearize(g);
        assertEquals("L=[[v8]] P=[v1], [v2], [v3] R=[[v4], [v5], [v6], [v7]]; [[v10], [v9]]", r.toString());
    }
}
