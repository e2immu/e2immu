package org.e2immu.analyser.bytecode.tools;

import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Source;
import org.e2immu.graph.analyser.TypeGraphIO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.analyser.bytecode.asm.MyClassVisitor.pathToFqn;
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
        Set<String> allPaths = allPaths();
        Map<String, String> typesInJMods = typesInJMods(allPaths);
        Map<String, URI> uriMap = composeUriMap(allPaths, artifactMap.values());
        loadByteCodeToDetermineDependentTypes(artifactMap, uriMap);
        Map<String, List<Artifact>> typesInArtifacts = new HashMap<>();
        for (Artifact a : artifactMap.values()) {
            if (a.types != null) {
                for (String type : a.types) {
                    typesInArtifacts.computeIfAbsent(type, t -> new ArrayList<>()).add(a);
                }
            }
        }
        Set<String> typesRequiredBySource = new HashSet<>();
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            TypeGraphIO.Node target = graph.getEdgeTarget(edge);
            typesRequiredBySource.add(target.getLabel());
        }
        fromSourceToCompileConfiguration(typesRequiredBySource, typesInArtifacts, typesInJMods);
        fromCompileConfigurationToRuntimeConfiguration(artifactMap, typesInArtifacts, typesInJMods);
        writeArtifacts(artifactMap.values(), a -> a.computedConfiguration);
        return 0;
    }

    private static final Comparator<Artifact> BEST_ARTIFACT = (a1, a2) -> {
        int c = a2.sourceTypesThatUseOneOfMyTypes - a1.sourceTypesThatUseOneOfMyTypes;
        if (c != 0) return c; // most used by other types
        if (a1.groupId.equals(a2.groupId) && a1.artifactId.equals(a2.artifactId)) {
            return a2.version.compareTo(a1.version); // highest version
        }
        return a1.compareTo(a2); // by key, alphabetically
    };

    private void fromCompileConfigurationToRuntimeConfiguration(Map<String, Artifact> artifactMap,
                                                                Map<String, List<Artifact>> typesInArtifacts,
                                                                Map<String, String> typesInJMods) {
        Map<String, Artifact> required = new HashMap<>();
        for (Artifact a : artifactMap.values()) {
            if (a.computedConfiguration == Configuration.COMPILE || a.computedConfiguration == Configuration.COMPILE_TRANSITIVE) {
                for (String d : a.dependentTypes) {
                    required.put(d, a);
                }
            }
        }
        int iteration = 0;
        Set<String> complainedAbout = new HashSet<>();
        Map<Artifact, Set<String>> cannotFind = new HashMap<>();
        while (true) {
            iteration++;
            Map<String, Artifact> newTypes = new HashMap<>();
            LOGGER.info("Iteration {}, have {} dependent types", iteration, required.size());
            Map<String, List<Artifact>> multiples = new HashMap<>();
            for (Map.Entry<String, Artifact> entry : required.entrySet()) {
                String type = entry.getKey();
                if (!typesInJMods.containsKey(type)) {
                    List<Artifact> artifacts = typesInArtifacts.get(type);
                    if (artifacts == null) {
                        cannotFind.computeIfAbsent(entry.getValue(), a -> new HashSet<>()).add(type);
                    } else if (artifacts.size() == 1) {
                        Artifact a = artifacts.get(0);
                        decideForRuntime(a, entry.getValue(), newTypes, complainedAbout);
                    } else {
                        multiples.put(type, artifacts);
                    }
                }
            }
            for (Map.Entry<String, List<Artifact>> entry : multiples.entrySet()) {
                Artifact best = entry.getValue().stream().min(BEST_ARTIFACT).orElseThrow();
                LOGGER.info("Choose {} for duplicate type {}", best, entry.getKey());
                decideForRuntime(best, required.get(entry.getKey()), newTypes, complainedAbout);
            }
            if (newTypes.isEmpty()) break;
            newTypes.forEach(required::putIfAbsent);
        }
        for (Map.Entry<Artifact, Set<String>> entry : cannotFind.entrySet()) {
            Artifact a = entry.getKey();
            LOGGER.error("Cannot find types in artifact {}, {} -> {}:", a, a.initialConfiguration, a.computedConfiguration);
            for (String type : entry.getValue()) {
                LOGGER.error("    - {}", type);
            }
        }
    }

    private void decideForRuntime(Artifact a, Artifact reason, Map<String, Artifact> newTypes, Set<String> complainedAbout) {
        if (a.computedConfiguration == null) {
            boolean transitive = a.initialConfiguration.transitive;
            a.computedConfiguration = transitive ? Configuration.RUNTIME_TRANSITIVE : Configuration.RUNTIME;
            for (String dt : a.dependentTypes) {
                newTypes.putIfAbsent(dt, a);
            }
        } else if (a.computedConfiguration != a.initialConfiguration && complainedAbout.add(a.key)) {
            LOGGER.error("Artifact {} can become RUNTIME", a);
        }
        a.reasonForInclusion.add(reason);
        a.sourceTypesThatUseOneOfMyTypes++; // additional dependency
    }

    private static void fromSourceToCompileConfiguration(Set<String> typesRequiredBySource, Map<String, List<Artifact>> typesInArtifacts, Map<String, String> typesInJMods) {
        LOGGER.info("Have {} types required by source code", typesRequiredBySource.size());
        Map<String, List<Artifact>> multiples = new HashMap<>();
        for (String type : typesRequiredBySource) {
            List<Artifact> artifacts = typesInArtifacts.get(type);
            if (artifacts == null) {
                if (!typesInJMods.containsKey(type)) {
                    LOGGER.error("Have no artifact for type required by source {}", type);
                }
            } else if (artifacts.size() > 1) {
                multiples.put(type, artifacts);
            } else {
                Artifact a = artifacts.get(0);
                decideForCompile(type, a);
            }
        }
        for (Map.Entry<String, List<Artifact>> entry : multiples.entrySet()) {
            Artifact best = entry.getValue().stream().min(BEST_ARTIFACT).orElseThrow();
            LOGGER.info("Choose {} for duplicate type {}", best, entry.getKey());
            decideForCompile(entry.getKey(), best);
        }
    }

    private static void decideForCompile(String type, Artifact a) {
        a.sourceTypesThatUseOneOfMyTypes++;
        if (a.computedConfiguration == null) {
            boolean transitive = a.initialConfiguration.transitive;
            a.computedConfiguration = transitive ? Configuration.COMPILE_TRANSITIVE : Configuration.COMPILE;
        }
        if (a.initialConfiguration != Configuration.COMPILE
                && a.initialConfiguration != Configuration.COMPILE_TRANSITIVE) {
            LOGGER.error("Artifact {} has configuration {}, should be COMPILE or COMPILE_TRANSITIVE; type is {}",
                    a, a.initialConfiguration, type);
        }
    }

    private Map<String, String> typesInJMods(Set<String> allPaths) throws IOException {
        Map<String, String> typeToJMod = new HashMap<>();
        for (String path : allPaths) {
            if (path.endsWith(".jmod")) {
                Resources resources = new Resources();
                URL url = Resources.constructJModURL(path, inputConfiguration.alternativeJREDirectory());
                resources.addJmod(url);
                LOGGER.info("Adding jmod uri {}", url);
                resources.visit(new String[]{}, (prefix, uris) -> {
                    URI uri = uris.get(0);
                    if (uri.toString().endsWith(".class") && !uri.toString().endsWith("/module-info.class")) {
                        String fqn = pathToFqn(String.join(".", prefix));
                        typeToJMod.putIfAbsent(fqn, path);
                    }
                });
            }
        }
        return typeToJMod;
    }

    private Set<String> allPaths() {
        Set<String> allPaths = new HashSet<>(inputConfiguration.classPathParts());
        allPaths.addAll(inputConfiguration.runtimeClassPathParts());
        allPaths.addAll(inputConfiguration.testClassPathParts());
        allPaths.addAll(inputConfiguration.testRuntimeClassPathParts());
        return allPaths;
    }

    private static void loadByteCodeToDetermineDependentTypes(Map<String, Artifact> artifactMap,
                                                              Map<String, URI> uriMap) throws IOException {
        for (Artifact a : artifactMap.values()) {
            a.uri = uriMap.get(a.key);
            if (a.uri != null) {
                Resources resources = new Resources();
                URL url = new URL("jar:file:" + a.uri + "!/");
                resources.addJar(url);
                Set<String> localTypes = new HashSet<>();
                Set<String> dependentTypes = new HashSet<>();
                ExtractTypesFromClassFile.MyClassVisitor myClassVisitor = new ExtractTypesFromClassFile
                        .MyClassVisitor(a.key, new ExtractTypesFromClassFile.GraphAction() {
                    @Override
                    public void typeInJar(String type, String jar) {
                        // not needed
                    }

                    @Override
                    public void typeDependsOnType(String from, String to, long value) {
                        dependentTypes.add(to);
                    }
                });
                resources.visit(new String[]{}, (prefix, uris) -> {
                    URI uri = uris.get(0);
                    if (uri.toString().endsWith(".class") && !uri.toString().endsWith("/module-info.class")) {
                        String fqn = pathToFqn(String.join(".", prefix));
                        localTypes.add(fqn);
                        LOGGER.debug("Parsing {} in jar {}", fqn, a.key);

                        Source source = resources.fqnToPath(fqn, ".class");
                        if (source != null && source.path() != null) {
                            byte[] classBytes = resources.loadBytes(source.path());
                            if (classBytes != null) {
                                ClassReader classReader = new ClassReader(classBytes);

                                classReader.accept(myClassVisitor, 0);
                                LOGGER.debug("Constructed class reader with {} bytes", classBytes.length);
                            } else {
                                LOGGER.warn("Skipping {}, no class bytes", uri);
                            }
                        } else {
                            LOGGER.debug("Skipping {}, empty source or source path: {}", fqn, uri);
                        }
                    }
                });
                Set<String> dependentTypesMinusMine = new HashSet<>(dependentTypes);
                dependentTypesMinusMine.removeAll(localTypes);
                a.types = Set.copyOf(localTypes);
                a.dependentTypes = Set.copyOf(dependentTypesMinusMine);
                LOGGER.info("** {} has {} types, {} dependent types", a, a.types.size(), a.dependentTypes.size());
            }
        }
    }

    private Map<String, URI> composeUriMap(Set<String> allPaths,
                                           Collection<Artifact> artifacts) throws URISyntaxException {
        Map<String, Artifact> artifactsByNameVersion = new HashMap<>();
        Set<String> duplicateNameVersions = new HashSet<>();
        for (Artifact artifact : artifacts) {
            String nameVersion = artifact.artifactId + "-" + artifact.version;
            Artifact prev = artifactsByNameVersion.put(nameVersion, artifact);
            if (prev != null) {
                LOGGER.error("Duplicate name-version {} and {}", prev, artifact);
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

                if (!duplicateNameVersions.contains(nameVersion) && fromNameVersion != null) {
                    String key = fromNameVersion.key;
                    URI uri = new URI(path);
                    map.put(key, uri);
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
            if (configuration != null) {
                String reason;
                if (configuration == Configuration.RUNTIME || configuration == Configuration.RUNTIME_TRANSITIVE) {
                    String reasons = artifact.reasonForInclusion.stream().map(a -> a.key)
                            .sorted().collect(Collectors.joining(", "));
                    reason = reasons.isBlank() ? "" : " < " + reasons;
                } else {
                    reason = "";
                }
                String line;
                if (configuration.transitive) {
                    Configuration nonTransitive = configuration.nonTransitive();
                    line = "//" + nonTransitive.gradle + " \"" + artifact.key + "\"  transitive" + reason;
                } else {
                    line = configuration.gradle + " \"" + artifact.key + "\"" + (reason.isBlank() ? "" : "//" + reason);
                }
                LOGGER.info(line);
            }
        }
        for (Artifact artifact : sorted) {
            Configuration configuration = configurationFunction.apply(artifact);
            if (configuration == null) {
                LOGGER.info("//UNUSED \"" + artifact.key + "\"");
            }
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
        private int sourceTypesThatUseOneOfMyTypes;
        private Set<Artifact> reasonForInclusion = new HashSet<>();

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

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            return o instanceof Artifact a && key.equals(a.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }
}

