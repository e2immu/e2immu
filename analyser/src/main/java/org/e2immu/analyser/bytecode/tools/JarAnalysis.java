package org.e2immu.analyser.bytecode.tools;

import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.graph.analyser.TypeGraphIO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.e2immu.graph.analyser.TypeGraphIO.createPackageGraph;

public class JarAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(JarAnalysis.class);
    private final InputConfiguration inputConfiguration;

    public JarAnalysis(InputConfiguration inputConfiguration) {
        this.inputConfiguration = inputConfiguration;
    }

    public int go(File typeGraphGml) throws IOException {
        LOGGER.info("Classpath: {}", inputConfiguration.classPathParts());
        LOGGER.info("Runtime classpath: {}", inputConfiguration.runtimeClassPathParts());
        LOGGER.info("Test classpath: {}", inputConfiguration.testClassPathParts());
        LOGGER.info("Test runtime classpath: {}", inputConfiguration.testRuntimeClassPathParts());
        LOGGER.info("Dependencies: {}", inputConfiguration.dependencies());

        Graph<TypeGraphIO.Node, DefaultWeightedEdge> graph = createPackageGraph();
        try (FileInputStream inputStream = new FileInputStream(typeGraphGml)) {
            TypeGraphIO.importGraph(inputStream, graph);
            LOGGER.info("Type graph has {} nodes", graph.vertexSet().size());
        }
        return 0;
    }
}
