/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.annotationxml.AnnotationStore;
import org.e2immu.analyser.annotationxml.AnnotationXmlReader;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.util.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Input {
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

    private final Configuration configuration;
    private final Resources classPath = new Resources();
    private final Resources sourcePath = new Resources();
    private final TypeContext globalTypeContext = new TypeContext();
    private final TypeStore sourceTypeStore = new MapBasedTypeStore();

    private final AnnotationStore annotationStore;
    private final ByteCodeInspector byteCodeInspector;
    private final List<URL> annotatedAPIs;
    private final Map<TypeInfo, URL> sourceURLs;

    public Input(Configuration configuration) throws IOException {
        this.configuration = configuration;
        initializeClassPath();
        assembleClassPath(classPath, "Classpath", configuration.inputConfiguration.classPathParts);
        annotationStore = new AnnotationXmlReader(classPath);
        LOGGER.info("Read {} annotations from annotation.xml files in classpath", annotationStore.getNumberOfAnnotations());
        byteCodeInspector = new ByteCodeInspector(classPath, annotationStore, globalTypeContext);
        preload("org.e2immu.annotation"); // needed for our own stuff
        preload("java.lang"); // there are needed to help with implicit imports

        assembleClassPath(sourcePath, "Source path", configuration.inputConfiguration.sources);
        sourceURLs = computeSourceURLs();

        annotatedAPIs = classPath.expandURLs("annotated_api");
        LOGGER.info("Found {} annotated_api files in class path", annotatedAPIs.size());
    }

    private Map<TypeInfo, URL> computeSourceURLs() {
        Map<TypeInfo, URL> sourceURLs = new HashMap<>();
        AtomicInteger ignored = new AtomicInteger();
        sourcePath.visit(new String[0], (parts, list) -> {
            if (parts.length >= 1) {
                String name = parts[parts.length - 1];
                if (name.endsWith(".java")) {
                    String typeName = name.substring(0, name.length() - 5);
                    String packageName = Arrays.stream(parts).limit(parts.length - 1).collect(Collectors.joining("."));
                    if (acceptSource(packageName)) {
                        TypeInfo typeInfo = new TypeInfo(packageName, typeName);
                        sourceTypeStore.add(typeInfo);
                        globalTypeContext.typeStore.add(typeInfo);
                        URL url = list.get(0);
                        sourceURLs.put(typeInfo, url);
                    } else {
                        ignored.incrementAndGet();
                    }
                }
            }
        });
        LOGGER.info("Found {} .java files in source path, skipped {}", sourceURLs.size(), ignored);
        return sourceURLs;
    }

    private boolean acceptSource(String packageName) {
        if (configuration.inputConfiguration.restrictSourceToPackages.isEmpty()) return true;
        for (String packageString : configuration.inputConfiguration.restrictSourceToPackages) {
            if (packageString.endsWith(".")) {
                if (packageName.startsWith(packageString)) return true;
            } else if (packageName.equals(packageString)) return true;
        }
        return false;
    }

    private void initializeClassPath() throws IOException {
        for (TypeInfo typeInfo : Primitives.PRIMITIVES.typeByName.values()) {
            globalTypeContext.typeStore.add(typeInfo);
            globalTypeContext.addToContext(typeInfo);
        }
        int entriesAdded = classPath.addJarFromClassPath("org/e2immu/annotation");
        if (entriesAdded < 10 || entriesAdded > 50) throw new RuntimeException("? expected at least 10 entries");
    }

    /**
     * IMPORTANT: this method assumes that the jmod 'java.base.jmod' is on the class path
     * if not, the method will have little effect and no classes beyond the ones from
     * <code>initializeClassPath</code> will be present
     */
    private void preload(String thePackage) {
        LOGGER.info("Start pre-loading {}", thePackage);
        AtomicInteger inspected = new AtomicInteger();
        classPath.expandLeaves(thePackage, ".class", (expansion, list) -> {
            // we'll loop over the primary types only
            if (!expansion[expansion.length - 1].contains("$")) {
                String fqn = ParseAndInspect.fqnOfClassFile(thePackage, expansion);
                TypeInfo typeInfo = (TypeInfo) globalTypeContext.getFullyQualified(fqn, false);
                if (typeInfo == null) {
                    String path = fqn.replace(".", "/"); // this is correct!
                    byteCodeInspector.inspectFromPath(path);
                    inspected.incrementAndGet();
                }
            }
        });
        LOGGER.info("... inspected {} paths", inspected);
    }

    private void assembleClassPath(Resources classPath, String msg, List<String> parts) throws IOException {
        for (String part : parts) {
            if (part.startsWith(JAR_WITH_PATH_PREFIX)) {
                classPath.addJarFromClassPath(part.substring(JAR_WITH_PATH_PREFIX.length()));
            } else if (part.endsWith(".jar")) {
                try {
                    // "jar:file:build/libs/equivalent.jar!/"
                    URL url = new URL("jar:file:" + part + "!/");
                    classPath.addJar(url);
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
                        if (configuration.inputConfiguration.alternativeJREDirectory == null) {
                            jre = System.getProperty("java.home");
                        } else {
                            jre = configuration.inputConfiguration.alternativeJREDirectory;
                        }
                        if (!jre.endsWith("/")) jre = jre + "/";
                        url = new URL("jar:file:" + jre + part + "!/");
                    }
                    int entries = classPath.addJmod(url);
                    LOGGER.debug("Added {} entries for jmod {}", entries, part);
                } catch (IOException e) {
                    LOGGER.error("{} part '{}' ignored: IOException {}", msg, part, e.getMessage());
                }
            } else {
                File directory = new File(part);
                if (directory.isDirectory()) {
                    LOGGER.info("Adding {} to classpath", directory.getAbsolutePath());
                    classPath.addDirectoryFromFileSystem(directory);
                } else {
                    LOGGER.error("{} part '{}' is not a .jar file, and not a directory: ignored", msg, part);
                }
            }
        }
    }

    public List<URL> getAnnotatedAPIs() {
        return annotatedAPIs;
    }

    public Map<TypeInfo, URL> getSourceURLs() {
        return sourceURLs;
    }

    public Resources getClassPath() {
        return classPath;
    }

    public TypeContext getGlobalTypeContext() {
        return globalTypeContext;
    }

    public Resources getSourcePath() {
        return sourcePath;
    }

    public ByteCodeInspector getByteCodeInspector() {
        return byteCodeInspector;
    }

    public AnnotationStore getAnnotationStore() {
        return annotationStore;
    }

    public TypeStore getSourceTypeStore() {
        return sourceTypeStore;
    }
}
