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
}
