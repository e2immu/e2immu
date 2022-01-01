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
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.bytecode.OnDemandInspection;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.inspector.ParseAndInspect;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.impl.TypeMapImpl;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.e2immu.analyser.inspector.InspectionState.TRIGGER_JAVA_PARSER;

public record Input(Configuration configuration,
                    TypeContext globalTypeContext,
                    OnDemandInspection byteCodeInspector,
                    Map<TypeInfo, URL> annotatedAPIs,
                    Map<TypeInfo, URL> sourceURLs,
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
        Resources classPath = assemblePath(configuration, true, "Classpath",
                configuration.inputConfiguration().classPathParts());
        AnnotationStore annotationStore = new AnnotationXmlReader(classPath, configuration.annotationXmlConfiguration());
        LOGGER.info("Read {} annotations from 'annotation.xml' files in classpath", annotationStore.getNumberOfAnnotations());
        TypeContext globalTypeContext = new TypeContext(new TypeMapImpl.Builder(classPath));
        OnDemandInspection byteCodeInspector = new ByteCodeInspector(classPath, annotationStore, globalTypeContext);
        globalTypeContext.typeMap.setByteCodeInspector(byteCodeInspector);
        globalTypeContext.loadPrimitives();
        for (String packageName : new String[]{"org.e2immu.annotation", "java.lang", "java.util.function"}) {
            preload(globalTypeContext, byteCodeInspector, classPath, packageName); // needed for our own stuff
        }

        return createNext(configuration, classPath, globalTypeContext, byteCodeInspector);
    }

    public static Input createNext(Configuration configuration,
                                   Resources classPath,
                                   TypeContext globalTypeContext,
                                   OnDemandInspection byteCodeInspector) throws IOException {
        Resources sourcePath = assemblePath(configuration, false, "Source path",
                configuration.inputConfiguration().sources());
        Trie<TypeInfo> sourceTypes = new Trie<>();
        Map<TypeInfo, URL> sourceURLs = computeSourceURLs(sourcePath, globalTypeContext,
                configuration.inputConfiguration().restrictSourceToPackages(), sourceTypes, "source path");

        Resources annotatedAPIsPath = assemblePath(configuration, false, "Annotated APIs path",
                configuration.annotatedAPIConfiguration().annotatedAPISourceDirs());
        Trie<TypeInfo> annotatedAPITypes = new Trie<>();
        Map<TypeInfo, URL> annotatedAPIs = computeSourceURLs(annotatedAPIsPath, globalTypeContext,
                configuration.annotatedAPIConfiguration().readAnnotatedAPIPackages(),
                annotatedAPITypes, "annotated API path");

        return new Input(configuration, globalTypeContext, byteCodeInspector, annotatedAPIs, sourceURLs, sourceTypes,
                annotatedAPITypes, classPath);
    }

    private static Map<TypeInfo, URL> computeSourceURLs(Resources sourcePath,
                                                        TypeContext globalTypeContext,
                                                        List<String> restrictions,
                                                        Trie<TypeInfo> trie,
                                                        String what) {
        Map<TypeInfo, URL> sourceURLs = new HashMap<>();
        AtomicInteger ignored = new AtomicInteger();
        sourcePath.visit(new String[0], (parts, list) -> {
            if (parts.length >= 1) {
                int n = parts.length - 1;
                String name = parts[n];
                if (name.endsWith(".java")) {
                    String typeName = name.substring(0, name.length() - 5);
                    String packageName = Arrays.stream(parts).limit(n).collect(Collectors.joining("."));
                    if (acceptSource(packageName, typeName, restrictions)) {
                        TypeInfo typeInfo = new TypeInfo(packageName, typeName);
                        globalTypeContext.typeMap.add(typeInfo, TRIGGER_JAVA_PARSER);
                        URL url = list.get(0);
                        sourceURLs.put(typeInfo, url);
                        parts[n] = typeName;
                        trie.add(parts, typeInfo);
                    } else {
                        ignored.incrementAndGet();
                    }
                }
            }
        });
        LOGGER.info("Found {} .java files in {}, skipped {}", sourceURLs.size(), what, ignored);
        trie.freeze();
        return sourceURLs;
    }

    private static boolean acceptSource(String packageName, String typeName, List<String> restrictions) {
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
                               OnDemandInspection byteCodeInspector,
                               Resources classPath,
                               String thePackage) {
        LOGGER.info("Start pre-loading {}", thePackage);
        AtomicInteger inspected = new AtomicInteger();
        classPath.expandLeaves(thePackage, ".class", (expansion, list) -> {
            // we'll loop over the primary types only
            if (!expansion[expansion.length - 1].contains("$")) {
                String fqn = ParseAndInspect.fqnOfClassFile(thePackage, expansion);
                TypeInfo typeInfo = globalTypeContext.getFullyQualified(fqn, true);
                if (!typeInfo.typeInspection.isSet()) {
                    String path = fqn.replace(".", "/"); // this is correct!
                    byteCodeInspector.inspectFromPath(path);
                    inspected.incrementAndGet();
                }
            }
        });
        LOGGER.info("... inspected {} paths", inspected);
    }

    private static Resources assemblePath(Configuration configuration,
                                          boolean isClassPath,
                                          String msg,
                                          List<String> parts) throws IOException {
        Resources resources = new Resources();
        if (isClassPath) {
            int entriesAdded = resources.addJarFromClassPath("org/e2immu/annotation");
            if (entriesAdded < 10) throw new RuntimeException("? expected at least 10 entries");
        }
        for (String part : parts) {
            if (part.startsWith(JAR_WITH_PATH_PREFIX)) {
                resources.addJarFromClassPath(part.substring(JAR_WITH_PATH_PREFIX.length()));
            } else if (part.endsWith(".jar")) {
                try {
                    // "jar:file:build/libs/equivalent.jar!/"
                    URL url = new URL("jar:file:" + part + "!/");
                    resources.addJar(url);
                } catch (IOException e) {
                    LOGGER.error("{} part '{}' ignored: IOException {}", msg, part, e.getMessage());
                }
            } else if (part.endsWith(".jmod")) {
                try {
                    URL url;
                    if (part.startsWith("/")) {
                        url = new URL("jar:file:" + part + "!/");
                    } else {
                        String jre;
                        if (configuration.inputConfiguration().alternativeJREDirectory() == null) {
                            jre = System.getProperty("java.home");
                        } else {
                            jre = configuration.inputConfiguration().alternativeJREDirectory();
                        }
                        if (!jre.endsWith("/")) jre = jre + "/";
                        url = new URL("jar:file:" + jre + part + "!/");
                    }
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
}
