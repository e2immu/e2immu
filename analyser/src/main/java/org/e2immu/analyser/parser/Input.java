/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.annotationxml.AnnotationStore;
import org.e2immu.analyser.annotationxml.AnnotationXmlReader;
import org.e2immu.analyser.bytecode.asm.ByteCodeInspectorImpl;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.impl.TypeContextImpl;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.impl.TypeMapImpl;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.inspector.InspectionState.INIT_JAVA_PARSER;

public record Input(Configuration configuration,
                    TypeContext globalTypeContext,
                    ByteCodeInspector byteCodeInspector,
                    Map<TypeInfo, URI> annotatedAPIs,
                    Map<TypeInfo, URI> sourceURLs,
                    Trie<TypeInfo> sourceTypes,
                    Trie<TypeInfo> annotatedAPITypes,
                    Resources classPath) {

    private static final Logger LOGGER = LoggerFactory.getLogger(Input.class);

    /**
     * Use of this prefix in parts of the input classpath allows for adding jars
     * on the current classpath containing the path following the prefix.
     * <p>
     * For example, adding
     * <p>
     * Adds the content of
     * <p>
     * jar:file:/Users/bnaudts/.gradle/caches/modules-2/files-2.1/com.google.guava/guava/28.1-jre/b0e91dcb6a44ffb6221b5027e12a5cb34b841145/guava-28.1-jre.jar!/
     */
    public static final String JAR_WITH_PATH_PREFIX = "jar-on-classpath:";

    public static Input create(Configuration configuration) throws IOException {
        List<String> classPathAsList = classPathAsList(configuration.inputConfiguration());
        LOGGER.info("Combined classpath and test classpath has {} entries", classPathAsList.size());
        Resources classPath = assemblePath(configuration, true, "Classpath", classPathAsList);
        AnnotationStore annotationStore = new AnnotationXmlReader(classPath, configuration.annotationXmlConfiguration());
        LOGGER.info("Read {} annotations from 'annotation.xml' files in classpath",
                annotationStore.getNumberOfAnnotations());
        TypeMapImpl.Builder typeMap = new TypeMapImpl.Builder(classPath, configuration.parallel());
        TypeContextImpl globalTypeContext = new TypeContextImpl(typeMap);
        ByteCodeInspector byteCodeInspector = new ByteCodeInspectorImpl(classPath, annotationStore, globalTypeContext);
        globalTypeContext.typeMap().setByteCodeInspector(byteCodeInspector);
        globalTypeContext.loadPrimitives();
        for (String packageName : new String[]{"org.e2immu.annotation", "java.lang", "java.util.function"}) {
            preload(globalTypeContext, classPath, packageName); // needed for our own stuff
        }

        return createNext(configuration, classPath, globalTypeContext, byteCodeInspector);
    }

    public static Input createNext(Configuration configuration,
                                   Resources classPath,
                                   TypeContext globalTypeContext,
                                   ByteCodeInspector byteCodeInspector) throws IOException {
        Resources sourcePath = assemblePath(configuration, false, "Source path",
                configuration.inputConfiguration().sources());
        Resources testSourcePath = assemblePath(configuration, false, "Test source path",
                configuration.inputConfiguration().testSources());
        Trie<TypeInfo> sourceTypes = new Trie<>();
        Map<TypeInfo, URI> sourceURLs = computeSourceURLs(sourcePath, globalTypeContext,
                configuration.inputConfiguration().restrictSourceToPackages(), sourceTypes, "source path");
        Map<TypeInfo, URI> testSourceURLs = computeSourceURLs(testSourcePath, globalTypeContext,
                configuration.inputConfiguration().restrictTestSourceToPackages(), sourceTypes, "test source path");
        sourceURLs.putAll(testSourceURLs);
        sourceTypes.freeze();

        Resources annotatedAPIsPath = assemblePath(configuration, false, "Annotated APIs path",
                configuration.annotatedAPIConfiguration().annotatedAPISourceDirs());
        Trie<TypeInfo> annotatedAPITypes = new Trie<>();
        Map<TypeInfo, URI> annotatedAPIs = computeSourceURLs(annotatedAPIsPath, globalTypeContext,
                configuration.annotatedAPIConfiguration().readAnnotatedAPIPackages(),
                annotatedAPITypes, "annotated API path");
        annotatedAPITypes.freeze();

        return new Input(configuration, globalTypeContext, byteCodeInspector, Map.copyOf(annotatedAPIs),
                Map.copyOf(sourceURLs), sourceTypes, annotatedAPITypes, classPath);
    }

    /*
    Almost the same as create + createNext, but we keep the current global type context, the current primitives.
     */
    public Input copy(Configuration configuration) throws IOException {
        List<String> classPathAsList = classPathAsList(configuration.inputConfiguration());
        Resources classPath = assemblePath(configuration, true, "Classpath", classPathAsList);
        AnnotationStore annotationStore = new AnnotationXmlReader(classPath, configuration.annotationXmlConfiguration());
        ByteCodeInspector byteCodeInspector = new ByteCodeInspectorImpl(classPath, annotationStore, globalTypeContext);
        return createNext(configuration, classPath, globalTypeContext, byteCodeInspector);
    }

    /*
    TODO at some point, we may want to parameterize this: which types do we present to the analyser?
     */
    private static List<String> classPathAsList(InputConfiguration inputConfiguration) {
        Stream<String> compileCp = inputConfiguration.classPathParts().stream();
        Stream<String> runtimeCp = inputConfiguration.runtimeClassPathParts().stream();
        Stream<String> testCompileCp = inputConfiguration.testClassPathParts().stream();
        Stream<String> testRuntimeCp = inputConfiguration.testClassPathParts().stream();
        return Stream.concat(Stream.concat(compileCp, runtimeCp), Stream.concat(testCompileCp, testRuntimeCp))
                .distinct().toList();
    }

    private static Map<TypeInfo, URI> computeSourceURLs(Resources sourcePath,
                                                        TypeContext globalTypeContext,
                                                        List<String> restrictions,
                                                        Trie<TypeInfo> trie,
                                                        String what) {
        Map<TypeInfo, URI> sourceURLs = new HashMap<>();
        AtomicInteger ignored = new AtomicInteger();
        sourcePath.visit(new String[0], (parts, list) -> {
            if (parts.length >= 1) {
                int n = parts.length - 1;
                String name = parts[n];
                if (name.endsWith(".java") && !"package-info.java".equals(name)) {
                    String typeName = name.substring(0, name.length() - 5);
                    String packageName = Arrays.stream(parts).limit(n).collect(Collectors.joining("."));
                    if (acceptSource(packageName, typeName, restrictions)) {
                        URI uri = list.get(0);
                        TypeInfo typeInfo = new TypeInfo(Identifier.from(uri), packageName, typeName);
                        globalTypeContext.typeMap().add(typeInfo, INIT_JAVA_PARSER);
                        sourceURLs.put(typeInfo, uri);
                        parts[n] = typeName;
                        trie.add(parts, typeInfo);
                    } else {
                        ignored.incrementAndGet();
                    }
                }
            }
        });
        LOGGER.info("Found {} .java files in {}, skipped {}", sourceURLs.size(), what, ignored);
        return sourceURLs;
    }

    public static boolean acceptSource(String packageName, String typeName, List<String> restrictions) {
        if (restrictions.isEmpty()) return true;
        for (String packageString : restrictions) {
            if (packageString.endsWith(".")) {
                if (packageName.startsWith(packageString) ||
                    packageName.equals(packageString.substring(0, packageString.length() - 1))) return true;
            } else if (packageName.equals(packageString) || packageString.equals(packageName + "." + typeName))
                return true;
        }
        return false;
    }

    /**
     * IMPORTANT: this method assumes that the jmod 'java.base.jmod' is on the class path
     * if not, the method will have little effect and no classes beyond the ones from
     * <code>initializeClassPath</code> will be present
     */
    public static void preload(TypeContext globalTypeContext,
                               Resources classPath,
                               String thePackage) {
        LOGGER.info("Start pre-loading {}", thePackage);
        AtomicInteger inspected = new AtomicInteger();
        classPath.expandLeaves(thePackage, ".class", (expansion, list) -> {
            // we'll loop over the primary types only
            if (!expansion[expansion.length - 1].contains("$")) {
                String fqn = fqnOfClassFile(thePackage, expansion);
                assert Input.acceptFQN(fqn);
                // test against hard-coded types
                TypeInfo typeInfo = globalTypeContext.getFullyQualified(fqn, true);
                if (!typeInfo.typeInspection.isSet()) {
                    globalTypeContext.typeMap().getTypeInspection(typeInfo);
                    inspected.incrementAndGet();
                }
            }
        });
        LOGGER.info("... inspected {} paths", inspected);
    }

    public static String fqnOfClassFile(String prefix, String[] suffixes) {
        String combined = prefix + "." + String.join(".", suffixes).replaceAll("\\$", ".");
        if (combined.endsWith(".class")) {
            return combined.substring(0, combined.length() - 6);
        }
        throw new UnsupportedOperationException("Expected .class or .java file, but got " + combined);
    }

    private static Resources assemblePath(Configuration configuration,
                                          boolean isClassPath,
                                          String msg,
                                          List<String> parts) throws IOException {
        Resources resources = new Resources();
        if (isClassPath) {
            Map<String, Integer> entriesAdded = resources.addJarFromClassPath("org/e2immu/annotation");
            if (entriesAdded.size() != 1 || entriesAdded.values().stream().findFirst().orElseThrow() < 10) {
                throw new RuntimeException("? expected 1 jar, at least 10 entries");
            }
        }
        for (String part : parts) {
            if (part.startsWith(JAR_WITH_PATH_PREFIX)) {
                Map<String, Integer> entriesAdded = resources.addJarFromClassPath(part.substring(JAR_WITH_PATH_PREFIX.length()));
                LOGGER.debug("Found {} jar(s) on classpath for {}", entriesAdded.size(), part);
                entriesAdded.forEach((p, n) -> LOGGER.debug("  ... added {} entries for jar {}", n, p));
            } else if (part.endsWith(".jar")) {
                try {
                    // "jar:file:build/libs/equivalent.jar!/"
                    URL url = new URL("jar:file:" + part + "!/");
                    int entries = resources.addJar(url);
                    LOGGER.debug("Added {} entries for jar {}", entries, part);
                } catch (IOException e) {
                    LOGGER.error("{} part '{}' ignored: IOException {}", msg, part, e.getMessage());
                }
            } else if (part.endsWith(".jmod")) {
                try {
                    URL url = Resources.constructJModURL(part, configuration.inputConfiguration().alternativeJREDirectory());
                    int entries = resources.addJmod(url);
                    LOGGER.debug("Added {} entries for jmod {}", entries, part);
                } catch (IOException e) {
                    LOGGER.error("{} part '{}' ignored: IOException {}", msg, part, e.getMessage());
                }
            } else {
                File directory = new File(part);
                if (directory.isDirectory()) {
                    LOGGER.info("Adding {} to classpath", directory.getAbsolutePath());
                    resources.addDirectoryFromFileSystem(directory);
                } else {
                    LOGGER.error("{} part '{}' is not a .jar file, and not a directory: ignored", msg, part);
                }
            }
        }
        return resources;
    }

    public static boolean acceptFQN(String fqn) {
        return !fqn.startsWith("jdk.internal.");
    }
}
