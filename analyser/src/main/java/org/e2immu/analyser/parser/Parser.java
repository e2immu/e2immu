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
import org.e2immu.analyser.analyser.ShallowTypeAnalyser;
import org.e2immu.analyser.annotationxml.AnnotationXmlWriter;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.TypeMapVisitor;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.upload.AnnotationUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.log;


public class Parser {
    private static final Logger LOGGER = LoggerFactory.getLogger(Parser.class);

    public final Configuration configuration;
    private final Input input;
    private final Messages messages = new Messages();

    public Parser() throws IOException {
        // all the defaults will do...
        this(new Configuration.Builder().build());
    }

    public Parser(Configuration configuration) throws IOException {
        this.configuration = configuration;
        input = new Input(configuration);
    }

    public List<SortedType> run() throws IOException {
        LOGGER.info("Running with configuration: {}", configuration);
        Collection<URL> annotatedAPIs = input.getAnnotatedAPIs().values();
        if (!annotatedAPIs.isEmpty()) runAnnotatedAPIs(annotatedAPIs);
        return parseJavaFiles(input.getSourceURLs());
    }

    // method result only used separately in tests
    public void runAnnotatedAPIs(Collection<URL> annotatedAPIs) throws IOException {
        InspectAnnotatedAPIs inspectAnnotatedAPIs = new InspectAnnotatedAPIs(getTypeContext(), getByteCodeInspector());
        List<SortedType> sortedTypes = inspectAnnotatedAPIs.inspectResolvePossiblyMerge(annotatedAPIs, configuration.inputConfiguration.sourceEncoding);
        if (!configuration.skipAnalysis) {
            ensureShallowAnalysisOfLoadedObjects(sortedTypes);
        }
    }

    public List<SortedType> parseJavaFiles(Map<TypeInfo, URL> urls) {
        Map<TypeInfo, TypeContext> inspectedPrimaryTypesToTypeContextOfFile = new HashMap<>();
        ParseAndInspect parseAndInspect = new ParseAndInspect(getByteCodeInspector(), true, input.getSourceTypeStore());
        urls.forEach((typeInfo, url) -> typeInfo.typeInspection.setRunnable(() -> {
            if (!typeInfo.typeInspection.isSet()) {
                try {
                    LOGGER.info("Starting source code inspection of {}", url);
                    InputStreamReader isr = new InputStreamReader(url.openStream(), configuration.inputConfiguration.sourceEncoding);
                    String source = IOUtils.toString(isr);
                    TypeContext inspectionTypeContext = new TypeContext(getTypeContext());
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
        Resolver resolver = new Resolver();
        List<SortedType> sortedPrimaryTypes = resolver.sortTypes(inspectedPrimaryTypesToTypeContextOfFile);
        messages.addAll(resolver.getMessageStream());

        if (configuration.skipAnalysis) return sortedPrimaryTypes;

        ensureShallowAnalysisOfLoadedObjects();

        for (TypeMapVisitor typeMapVisitor : configuration.debugConfiguration.typeMapVisitors) {
            typeMapVisitor.visit(getTypeContext());
        }
        log(org.e2immu.analyser.util.Logger.LogTarget.ANALYSER, "Analysing primary types:\n{}",
                sortedPrimaryTypes.stream().map(t -> t.primaryType.fullyQualifiedName).collect(Collectors.joining("\n")));
        for (SortedType sortedType : sortedPrimaryTypes) {
            analyseSortedType(sortedType);
        }
        if (configuration.uploadConfiguration.upload) {
            AnnotationUploader annotationUploader = new AnnotationUploader(configuration.uploadConfiguration, getE2ImmuAnnotationExpressions());
            Map<String, String> map = annotationUploader.createMap(sortedPrimaryTypes.stream().map(sortedType -> sortedType.primaryType).collect(Collectors.toSet()));
            annotationUploader.writeMap(map);
        }
        if (configuration.annotationXmlConfiguration.writeAnnotationXml) {
            try {
                AnnotationXmlWriter.write(configuration.annotationXmlConfiguration, getTypeContext().typeStore);
            } catch (IOException ioe) {
                LOGGER.error("Caught ioe exception writing annotation XMLs");
                throw new RuntimeException(ioe);
            }
        }
        return sortedPrimaryTypes;
    }

    private void analyseSortedType(SortedType sortedType) {
        PrimaryTypeAnalyser primaryTypeAnalyser = new PrimaryTypeAnalyser(sortedType, configuration, getTypeContext().getPrimitives(), getE2ImmuAnnotationExpressions());
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

    private void ensureShallowAnalysisOfLoadedObjects(List<SortedType> sortedTypes) {

        // the following block of code ensures that primary types of the annotated APIs
        // are processed in the correct order

        List<TypeInfo> types = new LinkedList<>();
        sortedTypes.forEach(st -> types.add(st.primaryType));
        Set<TypeInfo> alreadyIncluded = new HashSet<>(types);

        getTypeContext().typeStore.visit(new String[0], (s, list) -> {
            for (TypeInfo typeInfo : list) {
                if (typeInfo.typeInspection.isSet() && !typeInfo.typeAnalysis.isSet() && !alreadyIncluded.contains(typeInfo)) {
                    types.add(typeInfo);
                }
            }
        });
        shallowAnalysis(types);
    }

    private void ensureShallowAnalysisOfLoadedObjects() {
        List<TypeInfo> types = new LinkedList<>();
        getTypeContext().typeStore.visit(new String[0], (s, list) -> {
            for (TypeInfo typeInfo : list) {
                if (typeInfo.typeInspection.isSet() && !typeInfo.typeAnalysis.isSet() && typeInfo.shallowAnalysis()) {
                    types.add(typeInfo);
                }
            }
        });
        shallowAnalysis(types);
    }

    private void shallowAnalysis(List<TypeInfo> types) {
        assert types.size() == new HashSet<>(types).size() : "Duplicates?";

        ShallowTypeAnalyser shallowTypeAnalyser = new ShallowTypeAnalyser(types, configuration,
                getTypeContext().getPrimitives(), getE2ImmuAnnotationExpressions());
        messages.addAll(shallowTypeAnalyser.analyse());

        assert types.stream().allMatch(typeInfo -> typeInfo.typeAnalysis.isSet() &&
                typeInfo.typeInspection.get().methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                        .allMatch(methodInfo -> methodInfo.methodAnalysis.isSet())) : "All method analysis set";
    }


    // only meant to be used in tests!!
    public TypeContext getTypeContext() {
        return input.getGlobalTypeContext();
    }

    // only meant to be used in tests!
    public ByteCodeInspector getByteCodeInspector() {
        return input.getByteCodeInspector();
    }

    public Stream<Message> getMessages() {
        return messages.getMessageStream();
    }

    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return input.getE2ImmuAnnotationExpressions();
    }
}
