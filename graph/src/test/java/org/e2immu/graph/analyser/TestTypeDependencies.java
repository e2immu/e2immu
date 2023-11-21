package org.e2immu.graph.analyser;

import org.e2immu.graph.EdgeIterator;
import org.e2immu.graph.EdgePrinter;
import org.e2immu.graph.G;
import org.e2immu.graph.op.BreakCycles;
import org.e2immu.graph.op.GreedyEdgeRemoval;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static org.e2immu.graph.analyser.TypeGraphIO.convertGraphToMap;
import static org.e2immu.graph.analyser.TypeGraphIO.createPackageGraph;

public class TestTypeDependencies {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTypeDependencies.class);

    // cp analyser/build/e2immuGraph/typeDependencies.gml analyser/build/e2immuGraph/packageDependenciesBasedOnTypeGraph.gml  graph/src/test/resources/org/e2immu/graph

    @Test
    public void test() throws IOException {
        test("org/e2immu/graph/typeDependencies.gml");
    }

    @Test
    public void test2() throws IOException {
        test("org/e2immu/graph/packageDependenciesBasedOnTypeGraph.gml");
    }

    private void test(String resourceName) throws IOException {
        Graph<TypeGraphIO.Node, DefaultWeightedEdge> graph = createPackageGraph();
        TypeGraphIO.importGraph(getClass().getClassLoader(), resourceName, graph);
        Map<TypeGraphIO.Node, Map<TypeGraphIO.Node, Long>> map = convertGraphToMap(graph);
        G<TypeGraphIO.Node> g = G.create(map);
        LOGGER.info("Have graph of {} nodes, {} edges", g.vertices().size(), g.edgeStream().count());
        EdgePrinter<TypeGraphIO.Node> edgePrinter = m -> m == null ? "[]"
                : m.entrySet().stream().map(e -> e.getKey() + "->" +
                        e.getValue().entrySet().stream().map(e2 -> e2.getKey() + ":"
                                + PackedInt.nice((int) (long) e2.getValue())).collect(Collectors.joining(",")))
                .collect(Collectors.joining(";"));
        long limit = PackedInt.FIELD.of(1);
        EdgeIterator<TypeGraphIO.Node> edgeIterator = gg ->
                BreakCycles.edgeIterator2(gg, Long::compareTo, limit, PackedInt::longSum);
        BreakCycles<TypeGraphIO.Node> bc = new BreakCycles<>(new GreedyEdgeRemoval<>(edgePrinter, edgeIterator));
        BreakCycles.Linearization<TypeGraphIO.Node> lin = bc.go(g);
    }

}
