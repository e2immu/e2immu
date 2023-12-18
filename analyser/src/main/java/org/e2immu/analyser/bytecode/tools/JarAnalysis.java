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

    /**
     * @param sourceTypeGraphGml must not be null
     * @param testTypeGraphGml   can be null, then the whole "test" part will be ignored
     * @return unix-style error code
     * @throws IOException        when reading the graphs
     * @throws URISyntaxException when parsing the class paths
     */
    public int go(File sourceTypeGraphGml, File testTypeGraphGml) throws IOException, URISyntaxException {
        LOGGER.info("Classpath size: {}", inputConfiguration.classPathParts().size());
        LOGGER.info("Runtime classpath size: {}", inputConfiguration.runtimeClassPathParts().size());
        if (checkRuntimeContainsCompileFails()) {
            return 1;
        }
        if (testTypeGraphGml == null) {
            LOGGER.info("Skipping test configurations, no test type graph");
        } else {
            LOGGER.info("Test classpath size: {}", inputConfiguration.testClassPathParts().size());
            if (checkTestContainsCompileFails()) {
                return 1;
            }
            LOGGER.info("Test runtime classpath size: {}", inputConfiguration.testRuntimeClassPathParts().size());
            if (checkTestRuntimeContainsTestFails()) {
                return 1;
            }
        }
        LOGGER.info("Dependencies size: {}", inputConfiguration.dependencies().size());

        Graph<TypeGraphIO.Node, DefaultWeightedEdge> sourceTypeGraph = readTypeGraph(sourceTypeGraphGml, "source");
        Graph<TypeGraphIO.Node, DefaultWeightedEdge> testTypeGraph = testTypeGraphGml == null ? null
                : readTypeGraph(testTypeGraphGml, "test");

        Map<String, Artifact> artifactMap = readArtifactsFromDependencies();
        logArtifacts(artifactMap.values(), true);

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
        for (DefaultWeightedEdge edge : sourceTypeGraph.edgeSet()) {
            TypeGraphIO.Node target = sourceTypeGraph.getEdgeTarget(edge);
            typesRequiredBySource.add(target.getLabel());
        }
        fromSourceOrTestToConfiguration(true, typesRequiredBySource, typesInArtifacts, typesInJMods);
        intoRuntimeConfiguration(true, artifactMap, typesInArtifacts, typesInJMods);

        if (testTypeGraph != null) {
            Set<String> typesRequiredByTest = new HashSet<>();
            for (DefaultWeightedEdge edge : testTypeGraph.edgeSet()) {
                TypeGraphIO.Node target = testTypeGraph.getEdgeTarget(edge);
                typesRequiredByTest.add(target.getLabel());
            }
            fromSourceOrTestToConfiguration(false, typesRequiredByTest, typesInArtifacts, typesInJMods);
            intoRuntimeConfiguration(false, artifactMap, typesInArtifacts, typesInJMods);
        }
        for (Artifact a : artifactMap.values()) {
            if (a.computedConfiguration != null && a.computedConfiguration.transitive
                    && a.isRecursivelyUnused(new HashSet<>())) {
                Configuration cc = a.computedConfiguration;
                a.computedConfiguration = cc.nonTransitive();
                LOGGER.info("Changing {} from {} to {}, parents in graph unused", a, cc, a.computedConfiguration);
            }
        }
        logArtifacts(artifactMap.values(), false);
        return 0;
    }

    private boolean checkTestRuntimeContainsTestFails() {
        Set<String> testWithoutJmod = inputConfiguration.testClassPathParts().stream()
                .filter(p -> !p.endsWith(".jmod")).collect(Collectors.toUnmodifiableSet());
        if (!new HashSet<>(inputConfiguration.testRuntimeClassPathParts()).containsAll(testWithoutJmod)) {
            LOGGER.error("Working on the assumption that the test runtime classpath fully contains the test class path");
            Set<String> test = new HashSet<>(testWithoutJmod);
            inputConfiguration.testRuntimeClassPathParts().forEach(test::remove);
            LOGGER.error("On test path, but not on test runtime path:");
            test.forEach(c -> LOGGER.error("   - {}", c));
            return true;
        }
        return false;
    }

    private boolean checkTestContainsCompileFails() {
        Set<String> compileWithoutJmod = inputConfiguration.classPathParts().stream()
                .filter(p -> !p.endsWith(".jmod")).collect(Collectors.toUnmodifiableSet());
        if (!new HashSet<>(inputConfiguration.testClassPathParts()).containsAll(compileWithoutJmod)) {
            LOGGER.error("Working on the assumption that the test classpath fully contains the compile class path");
            Set<String> compile = new HashSet<>(compileWithoutJmod);
            inputConfiguration.testClassPathParts().forEach(compile::remove);
            LOGGER.error("On compile path, but not on test path:");
            compile.forEach(c -> LOGGER.error("   - {}", c));
            return true;
        }
        return false;
    }

    private boolean checkRuntimeContainsCompileFails() {
        Set<String> compileWithoutJmod = inputConfiguration.classPathParts().stream()
                .filter(p -> !p.endsWith(".jmod")).collect(Collectors.toUnmodifiableSet());
        if (!new HashSet<>(inputConfiguration.runtimeClassPathParts()).containsAll(compileWithoutJmod)) {
            LOGGER.error("Working on the assumption that the runtime classpath fully contains the compile class path");
            Set<String> compile = new HashSet<>(compileWithoutJmod);
            inputConfiguration.runtimeClassPathParts().forEach(compile::remove);
            LOGGER.error("On compile path, but not in runtime path:");
            compile.forEach(c -> LOGGER.error("   - {}", c));
            return true;
        }
        return false;
    }

    private static Graph<TypeGraphIO.Node, DefaultWeightedEdge> readTypeGraph(File graphGml, String label)
            throws IOException {
        Graph<TypeGraphIO.Node, DefaultWeightedEdge> graph = createPackageGraph();
        try (FileInputStream inputStream = new FileInputStream(graphGml)) {
            TypeGraphIO.importGraph(inputStream, graph);
            LOGGER.info("{} graph nodes: {}", label, graph.vertexSet().size());
        }
        return graph;
    }

    private static final Comparator<Artifact> BEST_ARTIFACT = (a1, a2) -> {
        int c = a2.typesThatUseOneOfMyTypes - a1.typesThatUseOneOfMyTypes;
        if (c != 0) return c; // most used by other types
        if (a1.groupId.equals(a2.groupId) && a1.artifactId.equals(a2.artifactId)) {
            return a2.version.compareTo(a1.version); // highest version
        }
        return a1.compareTo(a2); // by key, alphabetically
    };

    private void intoRuntimeConfiguration(boolean compileOrTest,
                                          Map<String, Artifact> artifactMap,
                                          Map<String, List<Artifact>> typesInArtifacts,
                                          Map<String, String> typesInJMods) {
        String label = compileOrTest ? "compile" : "test";
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
            LOGGER.info("Iteration {} for {}, have {} dependent types", iteration, label, required.size());
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
                if (complainedAbout.add(best + "<-" + entry.getValue())) {
                    LOGGER.info("Choose {} for duplicate type {} in {}", best, entry.getKey(), label);
                }
                decideForRuntime(best, required.get(entry.getKey()), newTypes, complainedAbout);
            }
            if (newTypes.isEmpty()) break;
            newTypes.forEach(required::putIfAbsent);
        }
        for (Map.Entry<Artifact, Set<String>> entry : cannotFind.entrySet()) {
            Artifact a = entry.getKey();
            LOGGER.error("{}, cannot find types in artifact {}, {} -> {}:", label, a, a.initialConfiguration,
                    a.computedConfiguration);
            int cnt = 0;
            for (String type : entry.getValue()) {
                LOGGER.error("    - {}", type);
                cnt++;
                if (cnt > 20) {
                    LOGGER.error("    - ... in total {}", entry.getValue().size());
                    break;
                }
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
        a.typesThatUseOneOfMyTypes++; // additional dependency
    }

    private static void fromSourceOrTestToConfiguration(boolean compileOrTest,
                                                        Set<String> typesRequired,
                                                        Map<String, List<Artifact>> typesInArtifacts,
                                                        Map<String, String> typesInJMods) {
        String label = compileOrTest ? "compile" : "test";
        LOGGER.info("Have {} types required by {} code", typesRequired.size(), label);
        Map<String, List<Artifact>> multiples = new HashMap<>();
        for (String type : typesRequired) {
            List<Artifact> artifacts = typesInArtifacts.get(type);
            if (artifacts == null) {
                if (!typesInJMods.containsKey(type)) {
                    LOGGER.error("Have no artifact for type required by {} {}", label, type);
                }
            } else if (artifacts.size() > 1) {
                multiples.put(type, artifacts);
            } else {
                Artifact a = artifacts.get(0);
                decideForCompileOrTest(compileOrTest, type, a);
            }
        }
        for (Map.Entry<String, List<Artifact>> entry : multiples.entrySet()) {
            Artifact best = entry.getValue().stream().min(BEST_ARTIFACT).orElseThrow();
            LOGGER.info("In {}, choose {} for duplicate type {}", label, best, entry.getKey());
            decideForCompileOrTest(compileOrTest, entry.getKey(), best);
        }
    }

    private static void decideForCompileOrTest(boolean compileOrTest, String type, Artifact a) {
        a.typesThatUseOneOfMyTypes++;
        Configuration ic = a.initialConfiguration;
        if (a.computedConfiguration == null) {
            boolean transitive = ic.transitive;
            a.computedConfiguration = compileOrTest
                    ? (transitive ? Configuration.COMPILE_TRANSITIVE : Configuration.COMPILE)
                    : (transitive ? Configuration.TEST_TRANSITIVE : Configuration.TEST);
        }
        if (compileOrTest) {
            if (ic != Configuration.COMPILE && ic != Configuration.COMPILE_TRANSITIVE) {
                LOGGER.error("Artifact {} has configuration {}, should be COMPILE or COMPILE_TRANSITIVE; type is {}",
                        a, ic, type);
            }
        } else if (ic.runtime) {
            LOGGER.error("Artifact {} has configuration {}, should not be runtime; type is {}", a, ic, type);
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

    private Map<String, Artifact> readArtifactsFromDependencies() {
        Map<String, Artifact> map = new HashMap<>();
        Map<String, Configuration> configurationMap = Arrays.stream(JarAnalysis.Configuration.values())
                .collect(Collectors.toUnmodifiableMap(c -> c.abbrev, c -> c));
        for (String dependency : inputConfiguration.dependencies()) {
            int bracket = dependency.indexOf("[-");
            String withoutExclusion = bracket < 0 ? dependency : dependency.substring(0, bracket);
            String exclusions = bracket < 0 ? "" : dependency.substring(bracket + 2, dependency.length() - 1);
            String[] parts = withoutExclusion.split(":");
            if (parts.length != 4) {
                throw new UnsupportedOperationException("Expect 4 parts in '" + dependency + "'");
            }
            Configuration initialConfig = configurationMap.get(parts[3]);
            Artifact artifact = createArtifact(dependency, initialConfig, parts, exclusions);
            map.put(artifact.key, artifact);
        }
        return map;
    }

    private static Artifact createArtifact(String dependency,
                                           Configuration initialConfig,
                                           String[] parts,
                                           String exclusionGroup) {
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
        Set<ExcludeRule> excludeRules;
        if (exclusionGroup.isBlank()) {
            excludeRules = Set.of();
        } else {
            excludeRules = Arrays.stream(exclusionGroup.split(";"))
                    .map(ExcludeRule::from).collect(Collectors.toUnmodifiableSet());
        }
        return new Artifact(groupId, artifactId, version, initialConfig, excludeRules);
    }

    private void logArtifacts(Collection<Artifact> artifacts, boolean initial) {
        List<Artifact> sorted = artifacts.stream().sorted().toList();
        for (Artifact artifact : sorted) {
            Configuration configuration = initial ? artifact.initialConfiguration : artifact.computedConfiguration;
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
                    // //runtimeOnly "org.beanshell:bsh-core:2.0b4"  transitive < org.owasp.esapi:esapi:2.0.1
                    Configuration nonTransitive = configuration.nonTransitive();
                    line = "//" + nonTransitive.gradle + " \"" + artifact.key + "\"  transitive" + reason;
                } else {
                    String reasonOrEmpty = reason.isBlank() ? "" : "//" + reason;
                    if (!artifact.excludeRules.isEmpty()) {
                        line = configuration.gradle + " (\"" + artifact.key + "\") {" + reasonOrEmpty
                                + "\n" + artifact.excludeRules.stream()
                                .map(er -> "  exclude group: \"" + er.group + "\", module: \"" + er.module + "\"\n")
                                .collect(Collectors.joining())
                                + "}\n";
                    } else {
                        // runtimeOnly "org.checkerframework:checker-qual:3.5.0"// < com.google.guava:guava:28.1-jre
                        line = configuration.gradle + " \"" + artifact.key + "\"" + reasonOrEmpty;
                    }
                }
                LOGGER.info(line);
            }
        }
        for (Artifact artifact : sorted) {
            Configuration configuration = initial ? artifact.initialConfiguration : artifact.computedConfiguration;
            if (configuration == null && !artifact.initialConfiguration.transitive) {
                LOGGER.info("//UNUSED \"" + artifact.key + "\"");
            }
        }
    }

    public enum Configuration {
        COMPILE("implementation", "i", false, false),
        TEST("testImplementation", "ti", false, false),
        RUNTIME("runtimeOnly", "r", false, true),
        TEST_RUNTIME("testRuntimeOnly", "tr", false, true),
        COMPILE_TRANSITIVE("compileClasspath", "i-t", true, false),
        TEST_TRANSITIVE("testCompileClasspath", "ti-t", true, false),
        RUNTIME_TRANSITIVE("runtimeClasspath", "r-t", true, true),
        TEST_RUNTIME_TRANSITIVE("testRuntimeClasspath", "tr-t", true, true);

        public final String gradle;
        public final String abbrev;
        public final boolean transitive;
        public final boolean runtime;

        Configuration(String gradle, String abbrev, boolean transitive, boolean runtime) {
            this.gradle = gradle;
            this.abbrev = abbrev;
            this.transitive = transitive;
            this.runtime = runtime;
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

    record ExcludeRule(String group, String module) {
        public static ExcludeRule from(String s) {
            String[] split = s.split(":");
            return new ExcludeRule(split[0], split[1]);
        }
    }

    static class Artifact implements Comparable<Artifact> {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String key;
        private final Configuration initialConfiguration;
        private final Set<ExcludeRule> excludeRules;

        private URI uri;
        private Configuration computedConfiguration;
        private Set<String> types; // read from Jar
        private Set<String> dependentTypes; // computed
        private int typesThatUseOneOfMyTypes;
        private final Set<Artifact> reasonForInclusion = new HashSet<>();

        public Artifact(String groupId,
                        String artifactId,
                        String version,
                        Configuration initialConfiguration,
                        Set<ExcludeRule> excludeRules) {
            this.initialConfiguration = Objects.requireNonNull(initialConfiguration);
            this.excludeRules = Objects.requireNonNull(excludeRules);
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

        public boolean isRecursivelyUnused(Set<Artifact> done) {
            if (!done.add(this)) {
                return true;
            }
            if (computedConfiguration == null) {
                return true;
            }
            return reasonForInclusion.stream().allMatch(a -> a.isRecursivelyUnused(done));
        }
    }
}

