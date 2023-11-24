package org.e2immu.graph.analyser;

import org.e2immu.graph.EdgeIterator;
import org.e2immu.graph.EdgePrinter;
import org.e2immu.graph.G;
import org.e2immu.graph.op.BreakCycles;
import org.e2immu.graph.op.GreedyEdgeRemoval;
import org.e2immu.graph.util.TimedLogger;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Collectors;

import static org.e2immu.graph.analyser.TypeGraphIO.convertGraphToMap;
import static org.e2immu.graph.analyser.TypeGraphIO.createPackageGraph;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static final String CLASSPATH = "classpath:";

    public static void main(String[] args) throws IOException {
        new Main().go(args);
    }

    private void go(String[] args) throws IOException {
        try (InputStream inputStream = makeInputStream(args[0])) {
            test(inputStream);
        }
    }

    private InputStream makeInputStream(String location) throws IOException {
        if (location.startsWith(CLASSPATH)) {
            return this.getClass().getClassLoader().getResourceAsStream(location.substring(CLASSPATH.length()));
        }
        return new FileInputStream(location);
    }

    private static void test(InputStream inputStream) throws IOException {
        Graph<TypeGraphIO.Node, DefaultWeightedEdge> graph = createPackageGraph();
        TypeGraphIO.importGraph(inputStream, graph);
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
        TimedLogger timedLogger = new TimedLogger(LOGGER, 1000L);
        GreedyEdgeRemoval<TypeGraphIO.Node> action = new GreedyEdgeRemoval<>(edgePrinter, edgeIterator, timedLogger);
        BreakCycles<TypeGraphIO.Node> bc = new BreakCycles<>(action, timedLogger);
        BreakCycles.Linearization<TypeGraphIO.Node> lin = bc.go(g);
    }

}
