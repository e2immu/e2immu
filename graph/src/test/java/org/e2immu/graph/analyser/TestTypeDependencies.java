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
        Main.main(new String[]{Main.CLASSPATH + "org/e2immu/graph/typeDependencies.gml"});
    }

    @Test
    public void test2() throws IOException {
        Main.main(new String[]{Main.CLASSPATH + "org/e2immu/graph/packageDependenciesBasedOnTypeGraph.gml"});
    }


}
