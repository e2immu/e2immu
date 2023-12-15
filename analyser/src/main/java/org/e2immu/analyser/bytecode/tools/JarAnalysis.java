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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.graph.analyser.TypeGraphIO.createPackageGraph;

public class JarAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(JarAnalysis.class);
    private final InputConfiguration inputConfiguration;
    private final String fileSeparator;

    public JarAnalysis(InputConfiguration inputConfiguration, String fileSeparator) {
        this.inputConfiguration = inputConfiguration;
        this.fileSeparator = fileSeparator;
    }

    public int go(File typeGraphGml) throws IOException, URISyntaxException {
        LOGGER.info("Classpath size: {}", inputConfiguration.classPathParts().size());
        LOGGER.info("Runtime classpath size: {}", inputConfiguration.runtimeClassPathParts().size());
        LOGGER.info("Test classpath size: {}", inputConfiguration.testClassPathParts().size());
        LOGGER.info("Test runtime classpath size: {}", inputConfiguration.testRuntimeClassPathParts().size());
        LOGGER.info("Dependencies size: {}", inputConfiguration.dependencies().size());

        Graph<TypeGraphIO.Node, DefaultWeightedEdge> graph = createPackageGraph();
        try (FileInputStream inputStream = new FileInputStream(typeGraphGml)) {
            TypeGraphIO.importGraph(inputStream, graph);
            LOGGER.info("Type graph nodes: {}", graph.vertexSet().size());
        }
        Map<String, Artifact> artifactMap = readArtifacts();
        writeArtifacts(artifactMap.values(), a -> a.initialConfiguration);
        Map<String, URI> uriMap = composeUriMap(artifactMap.values());
        artifactMap.values().forEach(a -> a.uri = uriMap.get(a.key));
        return 0;
    }

    private Map<String, URI> composeUriMap(Collection<Artifact> artifacts) throws URISyntaxException {
        Set<String> allPaths = new HashSet<>(inputConfiguration.classPathParts());
        allPaths.addAll(inputConfiguration.runtimeClassPathParts());
        allPaths.addAll(inputConfiguration.testClassPathParts());
        allPaths.addAll(inputConfiguration.testRuntimeClassPathParts());
        Map<String, Artifact> artifactsByNameVersion = new HashMap<>();
        Set<String> duplicateNameVersions = new HashSet<>();
        for (Artifact artifact : artifacts) {
            String nameVersion = artifact.artifactId + "-" + artifact.version;
            Artifact prev = artifactsByNameVersion.put(nameVersion, artifact);
            if (prev != null) {
                LOGGER.info("Duplicate name-version {} and {}", prev, artifact);
                duplicateNameVersions.add(nameVersion);
            }
        }
        LOGGER.info("Have {} artifactsByNameVersion, {} duplicate, {} paths, file separator '{}'",
                artifactsByNameVersion.size(), duplicateNameVersions.size(), allPaths.size(), fileSeparator);
        Map<String, URI> map = new HashMap<>();
        for (String path : allPaths) {
            if (path.endsWith(".jar")) {
                // /Users/xxx/.gradle/caches/modules-2/files-2.1/org.graalvm.sdk/graal-sdk/21.2.0/a6f3d634a1a648e68824d5edcebf14b368f8db1b/graal-sdk-21.2.0.jar
                //  ~/.m2/repository/org/graalvm/sdk/graal-sdk/21.2.0/graal-sdk-21.2.0.jar
                int sep = path.lastIndexOf(fileSeparator);
                String nameVersion = path.substring(sep + 1, path.length() - 4);
                Artifact fromNameVersion = artifactsByNameVersion.get(nameVersion);
                String key;
                if (duplicateNameVersions.contains(nameVersion) || fromNameVersion == null) {
                    key = null;
                } else {
                    key = fromNameVersion.key;
                }
                if (key != null) {
                    URI uri = new URI(path);
                    map.put(key, uri);
                } else {
                    LOGGER.info("No URI for {}", key);
                }
            } // else: jmod, directory, ... simply ignore
        }
        LOGGER.info("Artifact key->URI: {}", map.size());
        return map;
    }

    private Map<String, Artifact> readArtifacts() {
        Map<String, Artifact> map = new HashMap<>();
        Map<String, Configuration> configurationMap = Arrays.stream(JarAnalysis.Configuration.values())
                .collect(Collectors.toUnmodifiableMap(c -> c.abbrev, c -> c));
        for (String dependency : inputConfiguration.dependencies()) {
            String[] parts = dependency.split(":");
            if (parts.length != 4) {
                throw new UnsupportedOperationException("Expect 4 parts in '" + dependency + "'");
            }
            Configuration initialConfig = configurationMap.get(parts[3]);
            Artifact artifact = createArtifact(dependency, initialConfig, parts);
            map.put(artifact.key, artifact);
        }
        return map;
    }

    private static Artifact createArtifact(String dependency, Configuration initialConfig, String[] parts) {
        if (initialConfig == null) {
            throw new UnsupportedOperationException("Cannot read configuration from '" + parts[3] + "'," +
                    " extracted from dependency '" + dependency + "'");
        }
        String groupId = parts[0];
        String artifactId = parts[1];
        String version = parts[2];
        if (groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
            throw new UnsupportedOperationException("Blank key: '" + dependency + "'");
        }
        return new Artifact(groupId, artifactId, version, initialConfig);
    }

    private void writeArtifacts(Collection<Artifact> artifacts, Function<Artifact, Configuration> configurationFunction) {
        List<Artifact> sorted = artifacts.stream().sorted().toList();
        for (Artifact artifact : sorted) {
            Configuration configuration = configurationFunction.apply(artifact);
            String line;
            if (configuration.transitive) {
                Configuration nonTransitive = configuration.nonTransitive();
                line = "//" + nonTransitive.gradle + " \"" + artifact.key + "\"  transitive";
            } else {
                line = configuration.gradle + " \"" + artifact.key + "\"";
            }
            LOGGER.info(line);
        }
    }

    public enum Configuration {
        COMPILE("implementation", "i", false),
        TEST("testImplementation", "ti", false),
        RUNTIME("runtimeOnly", "r", false),
        TEST_RUNTIME("testRuntimeOnly", "tr", false),
        COMPILE_TRANSITIVE("compileClasspath", "i-t", true),
        TEST_TRANSITIVE("testCompileClasspath", "ti-t", true),
        RUNTIME_TRANSITIVE("runtimeClasspath", "r-t", true),
        TEST_RUNTIME_TRANSITIVE("testRuntimeClasspath", "tr-t", true);

        public final String gradle;
        public final String abbrev;
        public final boolean transitive;

        Configuration(String gradle, String abbrev, boolean transitive) {
            this.gradle = gradle;
            this.abbrev = abbrev;
            this.transitive = transitive;
        }

        public Configuration nonTransitive() {
            return switch (this) {
                case COMPILE_TRANSITIVE -> COMPILE;
                case TEST_TRANSITIVE -> TEST;
                case RUNTIME_TRANSITIVE -> RUNTIME;
                case TEST_RUNTIME_TRANSITIVE -> TEST_RUNTIME;
                default -> throw new UnsupportedOperationException();
            };
        }
    }

    static class Artifact implements Comparable<Artifact> {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String key;
        private final Configuration initialConfiguration;

        private URI uri;
        private Configuration computedConfiguration;
        private Set<String> types; // read from Jar
        private Set<String> dependentTypes; // computed

        public Artifact(String groupId, String artifactId, String version, Configuration initialConfiguration) {
            this.initialConfiguration = Objects.requireNonNull(initialConfiguration);
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.key = groupId + ":" + artifactId + ":" + version;
        }

        @Override
        public int compareTo(Artifact o) {
            return key.compareTo(o.key);
        }

        @Override
        public String toString() {
            return key;
        }
    }
}

