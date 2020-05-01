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

import org.apache.commons.io.IOUtils;
import org.e2immu.analyser.analyser.TypeAnalyser;
import org.e2immu.analyser.annotationxml.AnnotationStore;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.upload.AnnotationUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class Parser {
    private static final Logger LOGGER = LoggerFactory.getLogger(Parser.class);

    public final Configuration configuration;
    private final Input input;
    private final TypeContext globalTypeContext;
    private final ByteCodeInspector byteCodeInspector;
    private final AnnotationStore annotationStore;
    private final TypeStore sourceTypeStore;

    public Parser() throws IOException {
        // all the defaults will do...
        this(new Configuration.Builder().build());
    }

    public Parser(Configuration configuration) throws IOException {
        this.configuration = configuration;
        input = new Input(configuration);
        globalTypeContext = input.getGlobalTypeContext();
        annotationStore = input.getAnnotationStore();
        byteCodeInspector = input.getByteCodeInspector();
        sourceTypeStore = input.getSourceTypeStore();
    }

    public List<SortedType> run() throws IOException {
        List<URL> annotatedAPIs = input.getAnnotatedAPIs();
        if (!annotatedAPIs.isEmpty()) runAnnotatedAPIs(annotatedAPIs);
        return parseJavaFiles(input.getSourceURLs());
    }

    public List<SortedType> runAnnotatedAPIs(List<URL> annotatedAPIs) throws IOException {
        InspectAnnotatedAPIs inspectAnnotatedAPIs = new InspectAnnotatedAPIs(globalTypeContext, byteCodeInspector);
        List<TypeInfo> types = inspectAnnotatedAPIs.inspect(annotatedAPIs, configuration.inputConfiguration.sourceEncoding);
        return types.stream().map(SortedType::new).collect(Collectors.toList());
    }

    public List<SortedType> parseJavaFiles(Map<TypeInfo, URL> urls) throws IOException {
        Map<TypeInfo, TypeContext> inspectedTypesToTypeContextOfFile = new HashMap<>();
        ParseAndInspect parseAndInspect = new ParseAndInspect(byteCodeInspector, true, sourceTypeStore);
        urls.forEach((typeInfo, url) -> {
            typeInfo.typeInspection.setRunnable(() -> {
                if (!typeInfo.typeInspection.isSetDoNotTriggerRunnable()) {
                    try {
                        LOGGER.info("Starting source code inspection of {}", url);
                        InputStreamReader isr = new InputStreamReader(url.openStream(), configuration.inputConfiguration.sourceEncoding);
                        String source = IOUtils.toString(isr);
                        TypeContext inspectionTypeContext = new TypeContext(globalTypeContext);
                        List<TypeInfo> types = parseAndInspect.phase1ParseAndInspect(inspectionTypeContext, url.toString(), source);
                        types.forEach(t -> inspectedTypesToTypeContextOfFile.put(t, inspectionTypeContext));
                    } catch (RuntimeException rte) {
                        LOGGER.warn("Caught runtime exception parsing and inspecting URL {}", url);
                        throw rte;
                    } catch (IOException ioe) {
                        LOGGER.warn("Stopping runnable because of an IOException parsing URL {}", url);
                        throw new RuntimeException(ioe);
                    }
                } else {
                    LOGGER.info("Source code inspection of {} already done", url);
                }
            });
        });
        // TODO this can be a bit more efficient
        urls.entrySet().stream().sorted(Comparator.comparing(e -> e.getValue().toString())).forEach(e -> e.getKey().typeInspection.get());
        return phase2ResolveAndAnalyse(inspectedTypesToTypeContextOfFile);
    }

    private List<SortedType> phase2ResolveAndAnalyse(Map<TypeInfo, TypeContext> inspectedTypesToTypeContextOfFile) {
        // phase 2: resolve methods and fields
        List<SortedType> sortedTypes = Resolver.sortTypes(inspectedTypesToTypeContextOfFile);

        // sort the types according to dependencies
        // within each type, sort the methods
        // phase 3: analyse all the types

        TypeAnalyser typeAnalyser = new TypeAnalyser(globalTypeContext);
        for (SortedType sortedType : sortedTypes) {
            try {
                typeAnalyser.analyse(sortedType);
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while analysing type {}", sortedType.typeInfo.fullyQualifiedName);
                throw rte;
            }
            try {
                if (!sortedType.typeInfo.isNestedType()) {
                    typeAnalyser.check(sortedType);
                } // else: nested types get checked after we've analysed their enclosing type
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while checking type {}", sortedType.typeInfo.fullyQualifiedName);
                throw rte;
            }
        }
        if (configuration.uploadConfiguration.upload) {
            AnnotationUploader annotationUploader = new AnnotationUploader(configuration.uploadConfiguration, globalTypeContext);
            annotationUploader.add(sortedTypes);
        }
        return sortedTypes;
    }

    // only meant to be used in tests!!
    public TypeContext getTypeContext() {
        return globalTypeContext;
    }

    // only meant to be used in tests!
    public ByteCodeInspector getByteCodeInspector() {
        return byteCodeInspector;
    }

    public List<Message> getMessages() {
        return globalTypeContext.getMessages();
    }
}
