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
import org.e2immu.analyser.analyser.PrimaryTypeAnalyser;
import org.e2immu.analyser.analyser.TypeAnalyser;
import org.e2immu.analyser.annotationxml.AnnotationXmlWriter;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.TypeContextVisitor;
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
import java.util.stream.Stream;


public class Parser {
    private static final Logger LOGGER = LoggerFactory.getLogger(Parser.class);

    public final Configuration configuration;
    private final Input input;
    private final TypeContext globalTypeContext;
    private final ByteCodeInspector byteCodeInspector;
    public final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final TypeStore sourceTypeStore;
    private final Messages messages = new Messages();

    public Parser() throws IOException {
        // all the defaults will do...
        this(new Configuration.Builder().build());
    }

    public Parser(Configuration configuration) throws IOException {
        this.configuration = configuration;
        input = new Input(configuration);
        globalTypeContext = input.getGlobalTypeContext();
        byteCodeInspector = input.getByteCodeInspector();
        sourceTypeStore = input.getSourceTypeStore();
        e2ImmuAnnotationExpressions = input.getE2ImmuAnnotationExpressions();
    }

    public List<SortedType> run() throws IOException {
        LOGGER.info("Running with configuration: {}", configuration);
        List<URL> annotatedAPIs = input.getAnnotatedAPIs();
        if (!annotatedAPIs.isEmpty()) runAnnotatedAPIs(annotatedAPIs);
        return parseJavaFiles(input.getSourceURLs());
    }

    // method result only used in tests
    public List<TypeInfo> runAnnotatedAPIs(List<URL> annotatedAPIs) throws IOException {
        InspectAnnotatedAPIs inspectAnnotatedAPIs = new InspectAnnotatedAPIs(globalTypeContext, byteCodeInspector);
        return inspectAnnotatedAPIs.inspect(annotatedAPIs, configuration.inputConfiguration.sourceEncoding);
    }

    public List<SortedType> parseJavaFiles(Map<TypeInfo, URL> urls) {
        Map<TypeInfo, TypeContext> inspectedPrimaryTypesToTypeContextOfFile = new HashMap<>();
        ParseAndInspect parseAndInspect = new ParseAndInspect(byteCodeInspector, true, sourceTypeStore);
        urls.forEach((typeInfo, url) -> typeInfo.typeInspection.setRunnable(() -> {
            if (!typeInfo.typeInspection.isSet()) {
                try {
                    LOGGER.info("Starting source code inspection of {}", url);
                    InputStreamReader isr = new InputStreamReader(url.openStream(), configuration.inputConfiguration.sourceEncoding);
                    String source = IOUtils.toString(isr);
                    TypeContext inspectionTypeContext = new TypeContext(globalTypeContext);
                    List<TypeInfo> primaryTypes = parseAndInspect.phase1ParseAndInspect(inspectionTypeContext, url.toString(), source);
                    primaryTypes.forEach(t -> inspectedPrimaryTypesToTypeContextOfFile.put(t, inspectionTypeContext));
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
        }));
        // TODO this can be a bit more efficient
        urls.entrySet().stream().sorted(Comparator.comparing(e -> e.getValue().toString())).forEach(e -> e.getKey().typeInspection.getPotentiallyRun());
        return phase2ResolveAndAnalyse(inspectedPrimaryTypesToTypeContextOfFile);
    }

    private List<SortedType> phase2ResolveAndAnalyse(Map<TypeInfo, TypeContext> inspectedPrimaryTypesToTypeContextOfFile) {
        // phase 2: resolve methods and fields
        Resolver resolver = new Resolver(false);
        List<SortedType> sortedPrimaryTypes = resolver.sortTypes(inspectedPrimaryTypesToTypeContextOfFile);
        messages.addAll(resolver.getMessageStream());

        if (configuration.skipAnalysis) return sortedPrimaryTypes;

        checkTypeAnalysisOfLoadedObjects();

        for (TypeContextVisitor typeContextVisitor : configuration.debugConfiguration.typeContextVisitors) {
            typeContextVisitor.visit(globalTypeContext);
        }

        for (SortedType sortedType : sortedPrimaryTypes) {
            PrimaryTypeAnalyser primaryTypeAnalyser = new PrimaryTypeAnalyser(sortedType, configuration, e2ImmuAnnotationExpressions);
            try {
                primaryTypeAnalyser.analyse();
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while analysing type {}", sortedType.primaryType.fullyQualifiedName);
                throw rte;
            }
            try {
                primaryTypeAnalyser.write();
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while making analysis immutable for type {}", sortedType.primaryType.fullyQualifiedName);
                throw rte;
            }
            try {
                primaryTypeAnalyser.check();
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while checking type {}", sortedType.primaryType.fullyQualifiedName);
                throw rte;
            }
            messages.addAll(primaryTypeAnalyser.getMessageStream());
        }
        if (configuration.uploadConfiguration.upload) {
            AnnotationUploader annotationUploader = new AnnotationUploader(configuration.uploadConfiguration, e2ImmuAnnotationExpressions);
            Map<String, String> map = annotationUploader.createMap(sortedPrimaryTypes.stream().map(sortedType -> sortedType.primaryType).collect(Collectors.toSet()));
            annotationUploader.writeMap(map);
        }
        if (configuration.annotationXmlConfiguration.writeAnnotationXml) {
            try {
                AnnotationXmlWriter.write(configuration.annotationXmlConfiguration, globalTypeContext.typeStore);
            } catch (IOException ioe) {
                LOGGER.error("Caught ioe exception writing annotation XMLs");
                throw new RuntimeException(ioe);
            }
        }
        return sortedPrimaryTypes;
    }

    private void checkTypeAnalysisOfLoadedObjects() {
        globalTypeContext.typeStore.visit(new String[0], (s, list) -> {
            for (TypeInfo typeInfo : list) {
                if (typeInfo.typeInspection.isSet() && !typeInfo.typeAnalysis.isSet() && !typeInfo.hasBeenDefined()) {
                    typeInfo.copyAnnotationsIntoTypeAnalysisProperties(e2ImmuAnnotationExpressions);
                }
            }
        });
    }


    // only meant to be used in tests!!
    public TypeContext getTypeContext() {
        return globalTypeContext;
    }

    // only meant to be used in tests!
    public ByteCodeInspector getByteCodeInspector() {
        return byteCodeInspector;
    }

    public Stream<Message> getMessages() {
        return messages.getMessageStream();
    }

    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return e2ImmuAnnotationExpressions;
    }
}
