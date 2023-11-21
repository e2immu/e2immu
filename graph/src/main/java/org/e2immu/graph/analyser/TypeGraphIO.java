package org.e2immu.graph.analyser;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.nio.gml.GmlImporter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class TypeGraphIO {

    public static Map<Node, Map<Node, Long>> convertGraphToMap(Graph<Node, DefaultWeightedEdge> graph) {
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
        return map;
    }

    public static void importGraph(InputStream inputStream,
                                   Graph<Node, DefaultWeightedEdge> graph) throws IOException {
        GmlImporter<Node, DefaultWeightedEdge> importer = new GmlImporter<>();
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(inputStream),
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
    }

    public static class Node {
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
            return label + " (" + weight + ")";
        }
    }

    public static Graph<Node, DefaultWeightedEdge> createPackageGraph() {
        return GraphTypeBuilder.<Node, DefaultWeightedEdge>directed()
                .allowingMultipleEdges(false)
                .allowingSelfLoops(true)
                .edgeClass(DefaultWeightedEdge.class)
                .weighted(true)
                .buildGraph();
    }
}
