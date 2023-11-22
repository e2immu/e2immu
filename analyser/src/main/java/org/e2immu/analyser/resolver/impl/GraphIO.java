package org.e2immu.analyser.resolver.impl;

import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.graph.G;
import org.e2immu.graph.V;
import org.e2immu.graph.analyser.PackedInt;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.gml.GmlExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class GraphIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphIO.class);
    public static final String JAVA_BASE_JMOD = "java.base.jmod";

    public static void dumpGraphs(File directory,
                                  G<TypeInfo> typeGraph,
                                  G<TypeInfo> externalTypeGraph,
                                  G<MethodInfo> methodCallGraph) {
        try {
            if (directory.mkdirs()) {
                LOGGER.info("Created directory {}", directory);
            }
            dumpTypeGraph(new File(directory, "typeDependencies.gml"), typeGraph);
            dumpTypeGraph(new File(directory, "externalTypeDependencies.gml"), externalTypeGraph);
            dumpPackageGraphBasedOnTypeGraph(new File(directory, "packageDependenciesBasedOnTypeGraph.gml"),
                    typeGraph);
            dumpPackageGraphBasedOnTypeGraph(new File(directory, "packageDependenciesBasedOnExternalTypeGraph.gml"),
                    externalTypeGraph);
            Map<String, Map<String, Long>> packageToJar = dumpPackageToJarGraphBasedOnTypeGraph(new File(directory,
                            "packageToJarDependenciesBasedOnExternalTypeGraph.gml"),
                    externalTypeGraph);
            dumpJarCentricUsageTable(new File(directory, "jarToPackage.txt"), packageToJar);
            dumpMethodCallGraph(new File(directory, "methodCalls.gml"), methodCallGraph);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static void dumpJarCentricUsageTable(File file, Map<String, Map<String, Long>> packageToJar) throws IOException {
        Map<String, Map<String, Long>> jarToPackage = new HashMap<>();
        Map<String, Long> jarScore = new HashMap<>();
        for (Map.Entry<String, Map<String, Long>> entry : packageToJar.entrySet()) {
            for (Map.Entry<String, Long> entry2 : entry.getValue().entrySet()) {
                Map<String, Long> map = jarToPackage.computeIfAbsent(entry2.getKey(), p -> new HashMap<>());
                map.merge(entry.getKey(), entry2.getValue(), PackedInt::longSum);
                jarScore.merge(entry2.getKey(), entry2.getValue(), PackedInt::longSum);
            }
        }
        List<String> jarsSorted = jarScore.keySet().stream().sorted(Comparator.comparingLong(jarScore::get).reversed()).toList();
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            for (String theJar : jarsSorted) {
                Long score = jarScore.get(theJar);
                Map<String, Long> packages = jarToPackage.get(theJar);
                List<String> packagesSorted = packages.keySet().stream().sorted(Comparator.comparingLong(packages::get).reversed())
                        .limit(3).toList();
                osw.append('"').append(theJar).append('"').append(',').append(PackedInt.nice((int) (long) score))
                        .append(',').append(Integer.toString(packages.size()));
                for (String toPackage : packagesSorted) {
                    osw.append(',').append(toPackage);
                }
                osw.append('\n');
            }
        }
    }

    private static Map<String, Map<String, Long>> dumpPackageToJarGraphBasedOnTypeGraph(File file, G<TypeInfo> typeGraph) throws IOException {
        Map<String, Map<String, Long>> aggregated = new HashMap<>();
        for (Map.Entry<V<TypeInfo>, Map<V<TypeInfo>, Long>> entry : typeGraph.edges()) {
            TypeInfo typeInfo = entry.getKey().someElement();
            Map<String, Long> toMap = aggregated.computeIfAbsent(typeInfo.packageName(), s -> new HashMap<>());
            for (Map.Entry<V<TypeInfo>, Long> e2 : entry.getValue().entrySet()) {
                TypeInfo target = e2.getKey().someElement();
                if (target.getIdentifier() instanceof Identifier.JarIdentifier jid) {
                    String jarName = jid.extractJarName();
                    if (jarName != null && !JAVA_BASE_JMOD.equals(jarName)) {
                        toMap.merge(jarName, e2.getValue(), PackedInt::longSum);
                    }
                }
            }
        }
        dumpPackageGraph(file, aggregated);
        return aggregated;
    }

    private static void dumpPackageGraphBasedOnTypeGraph(File file, G<TypeInfo> typeGraph) throws IOException {
        Map<String, Map<String, Long>> aggregated = new HashMap<>();
        for (Map.Entry<V<TypeInfo>, Map<V<TypeInfo>, Long>> entry : typeGraph.edges()) {
            TypeInfo typeInfo = entry.getKey().someElement();
            Map<String, Long> toMap = aggregated.computeIfAbsent(typeInfo.packageName(), s -> new HashMap<>());
            for (Map.Entry<V<TypeInfo>, Long> e2 : entry.getValue().entrySet()) {
                TypeInfo target = e2.getKey().someElement();
                toMap.merge(target.packageName(), e2.getValue(), PackedInt::longSum);
            }
        }
        dumpPackageGraph(file, aggregated);
    }

    private static Graph<String, DefaultWeightedEdge> createPackageGraph() {
        return GraphTypeBuilder.<String, DefaultWeightedEdge>directed()
                .allowingMultipleEdges(false)
                .allowingSelfLoops(true)
                .edgeClass(DefaultWeightedEdge.class)
                .weighted(true)
                .buildGraph();
    }

    private static void dumpPackageGraph(File file, Map<String, Map<String, Long>> aggregated) throws IOException {
        Graph<String, DefaultWeightedEdge> graph = createPackageGraph();
        for (Map.Entry<String, Map<String, Long>> entry : aggregated.entrySet()) {
            String from = entry.getKey();
            if (!graph.containsVertex(from)) graph.addVertex(from);
            for (Map.Entry<String, Long> e2 : entry.getValue().entrySet()) {
                String target = e2.getKey();
                if (!graph.containsVertex(target)) graph.addVertex(target);
                DefaultWeightedEdge e = graph.addEdge(from, target);
                graph.setEdgeWeight(e, e2.getValue());
            }
        }
        Function<String, Map<String, Attribute>> vertexAttributeProvider = (v) -> {
            Map<String, Attribute> map = new LinkedHashMap<>();
            map.put("label", DefaultAttribute.createAttribute(v));
            return map;
        };

        exportWeightedGraph(graph, vertexAttributeProvider, file);
    }

    private static void dumpTypeGraph(File file, G<TypeInfo> typeGraph) throws IOException {
        Graph<TypeInfo, DefaultWeightedEdge> graph =
                GraphTypeBuilder.<TypeInfo, DefaultWeightedEdge>directed()
                        .allowingMultipleEdges(false)
                        .allowingSelfLoops(true)
                        .edgeClass(DefaultWeightedEdge.class)
                        .weighted(true)
                        .buildGraph();
        for (Map.Entry<V<TypeInfo>, Map<V<TypeInfo>, Long>> entry : typeGraph.edges()) {
            TypeInfo typeInfo = entry.getKey().someElement();
            if (!graph.containsVertex(typeInfo)) graph.addVertex(typeInfo);
            for (Map.Entry<V<TypeInfo>, Long> e2 : entry.getValue().entrySet()) {
                TypeInfo target = e2.getKey().someElement();
                if (!graph.containsVertex(target)) graph.addVertex(target);
                DefaultWeightedEdge e = graph.addEdge(typeInfo, target);
                graph.setEdgeWeight(e, e2.getValue());
            }
        }
        Function<TypeInfo, Map<String, Attribute>> vertexAttributeProvider = (v) -> {
            Map<String, Attribute> map = new LinkedHashMap<>();
            map.put("label", DefaultAttribute.createAttribute(v.fullyQualifiedName));
            V<TypeInfo> vertex = typeGraph.vertex(v);
            Map<V<TypeInfo>, Long> edges = typeGraph.edges(vertex);
            // weight is the sum of the outgoing edge weights
            long sum = edges == null ? 0L : edges.values().stream().mapToLong(x -> x).sum();
            map.put("weight", DefaultAttribute.createAttribute(sum));
            return map;
        };
        exportWeightedGraph(graph, vertexAttributeProvider, file);
    }

    private static <T> void exportWeightedGraph(Graph<T, DefaultWeightedEdge> graph,
                                                Function<T, Map<String, Attribute>> vertexAttributeProvider,
                                                File file) throws IOException {
        GmlExporter<T, DefaultWeightedEdge> exporter = new GmlExporter<>();
        exporter.setParameter(GmlExporter.Parameter.EXPORT_VERTEX_LABELS, true);
        exporter.setParameter(GmlExporter.Parameter.EXPORT_CUSTOM_VERTEX_ATTRIBUTES, true);
        exporter.setVertexAttributeProvider(vertexAttributeProvider);
        exporter.setParameter(GmlExporter.Parameter.EXPORT_EDGE_WEIGHTS, false);
        exporter.setParameter(GmlExporter.Parameter.EXPORT_EDGE_LABELS, false);
        exporter.setParameter(GmlExporter.Parameter.EXPORT_CUSTOM_EDGE_ATTRIBUTES, true);
        exporter.setEdgeAttributeProvider(e -> {
            Map<String, Attribute> map = new LinkedHashMap<>();
            int w = (int) graph.getEdgeWeight(e);
            map.put("weight", DefaultAttribute.createAttribute(w));
            return map;
        });
        exporter.exportGraph(graph, file);
    }

    // directed graph without weights
    private static void dumpMethodCallGraph(File file, G<MethodInfo> methodCallGraph) throws IOException {
        Graph<MethodInfo, DefaultEdge> graph =
                GraphTypeBuilder.<MethodInfo, DefaultEdge>directed()
                        .allowingMultipleEdges(false)
                        .allowingSelfLoops(true)
                        .edgeClass(DefaultEdge.class)
                        .weighted(false)
                        .buildGraph();
        for (V<MethodInfo> v : methodCallGraph.vertices()) {
            graph.addVertex(v.someElement());
        }
        for (Map.Entry<V<MethodInfo>, Map<V<MethodInfo>, Long>> entry : methodCallGraph.edges()) {
            for (V<MethodInfo> to : entry.getValue().keySet()) {
                graph.addEdge(entry.getKey().someElement(), to.someElement());
            }
        }

        GmlExporter<MethodInfo, DefaultEdge> exporter = new GmlExporter<>();
        exporter.setParameter(GmlExporter.Parameter.EXPORT_VERTEX_LABELS, true);
        exporter.setVertexAttributeProvider((v) -> {
            Map<String, Attribute> map = new LinkedHashMap<>();
            map.put("label", DefaultAttribute.createAttribute(v.fullyQualifiedName));
            return map;
        });
        exporter.exportGraph(graph, file);
    }
}
