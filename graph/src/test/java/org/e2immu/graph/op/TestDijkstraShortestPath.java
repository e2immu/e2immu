package org.e2immu.graph.op;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class TestDijkstraShortestPath {

    @Test
    public void test() {
        DijkstraShortestPath d = new DijkstraShortestPath();
        Map<Integer, Map<Integer, Long>> edges = Map.of(
                0, Map.of(1, 1L, 2, 4L, 3, 5L),
                1, Map.of(2, 2L),
                2, Map.of(3, 1L),
                3, Map.of()
        );
        DijkstraShortestPath.EdgeProvider edgeProvider = i -> edges.get(i).entrySet().stream();
        long[] dist0 = d.shortestPath(4, edgeProvider, 0);
        assertArrayEquals(new long[]{0, 1, 3, 4}, dist0);
        long[] dist1 = d.shortestPath(4, edgeProvider, 1);
        assertArrayEquals(new long[]{Long.MAX_VALUE, 0, 2, 3}, dist1);
        long[] dist2 = d.shortestPath(4, edgeProvider, 2);
        assertArrayEquals(new long[]{Long.MAX_VALUE, Long.MAX_VALUE, 0, 1}, dist2);
    }
}
