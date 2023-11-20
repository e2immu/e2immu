package org.e2immu.analyser.util.graph;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.nio.gml.GmlImporter;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TestTypeDependencies {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTypeDependencies.class);

    @Test
    public void test() throws IOException {
        Graph<Node, DefaultWeightedEdge> graph = createPackageGraph();
        GmlImporter<Node, DefaultWeightedEdge> importer = new GmlImporter<>();
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("org/e2immu/analyser/util/graph/typeDependencies.gml");
             InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(inputStream),
                     StandardCharsets.UTF_8)) {
            importer.setVertexFactory(Node::new);
            importer.addVertexAttributeConsumer((pair, attribute) -> {
                if ("label".equals(pair.getSecond())) {
                    pair.getFirst().label = attribute.getValue();
                }
                if ("weight".equals(pair.getSecond())) {
                    pair.getFirst().weight = Long.parseLong(attribute.getValue());
                }
            });
            importer.importGraph(graph, reader);
        }
        Map<Node, Map<Node, Long>> map = new HashMap<>();
        for (Node node : graph.vertexSet()) {
            Set<DefaultWeightedEdge> edges = graph.edgesOf(node);
            Map<Node, Long> edgeMap = new HashMap<>();
            for (DefaultWeightedEdge d : edges) {
                Node to = graph.getEdgeTarget(d);
                long weight = (long) graph.getEdgeWeight(d);
                edgeMap.merge(to, weight, Long::sum);
            }
            map.put(node, edgeMap);
        }
        G<Node> g = G.create(map);
        BreakCycles<Node> bc = new BreakCycles<>(new BreakCycles.GreedyEdgeRemoval<>());
        BreakCycles.Linearization<Node> lin = bc.go(g);
    }

    private static class Node {
        final int id;
        String label;
        long weight;

        Node(int id) {
            this.id = id;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public void setWeight(long weight) {
            this.weight = weight;
        }

        @Override
        public String toString() {
            return id + " (" + weight + "): " + label;
        }
    }

    private static Graph<Node, DefaultWeightedEdge> createPackageGraph() {
        return GraphTypeBuilder.<Node, DefaultWeightedEdge>directed()
                .allowingMultipleEdges(false)
                .allowingSelfLoops(true)
                .edgeClass(DefaultWeightedEdge.class)
                .weighted(true)
                .buildGraph();
    }
}
