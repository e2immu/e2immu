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
import org.e2immu.analyser.inspector.ParseAndInspect;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.resolver.Resolver;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.upload.AnnotationUploader;
import org.e2immu.analyser.util.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.*;
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
        input = Input.create(configuration);
    }

    public List<SortedType> run() {
        LOGGER.info("Running with configuration: {}", configuration);

        // at this point, bytecode inspection has been run on the Java base packages,
        // and some of our own annotations.
        // other bytecode inspection will take place on-demand, in the background.

        // we start the inspection and resolution of AnnotatedAPIs (Java parser, but with $ classes)
        Collection<URL> annotatedAPIs = input.annotatedAPIs().values();
        List<SortedType> sortedAnnotatedAPITypes;
        if (annotatedAPIs.isEmpty()) {
            sortedAnnotatedAPITypes = List.of();
        } else {
            sortedAnnotatedAPITypes = inspectAndResolve(input.annotatedAPIs(), input.annotatedAPITypes(),
                    configuration.annotatedAPIConfiguration.reportWarnings, true);
        }

        // and the the inspection and resolution of Java sources (Java parser)
        List<SortedType> resolvedSourceTypes = inspectAndResolve(input.sourceURLs(), input.sourceTypes(), true, false);

        // creating the typeMap ensures that all inspections are set.
        TypeMap typeMap = input.globalTypeContext().typeMapBuilder.build();

        // finally, there is an analysis step


        if (!configuration.skipAnalysis) {
            // we pass on the Java sources for the PrimaryTypeAnalyser, while all other loaded types
            // will be sent to the ShallowAnalyser
            runShallowAnalyser(typeMap, sortedAnnotatedAPITypes);
            writeAndUpload(sortedAnnotatedAPITypes);
            runPrimaryTypeAnalyser(typeMap, resolvedSourceTypes);
            writeAndUpload(resolvedSourceTypes);
        }

        return resolvedSourceTypes;
    }

    public List<SortedType> inspectAndResolve(Map<TypeInfo, URL> urls, Trie<TypeInfo> typesForWildcardImport,
                                              boolean reportWarnings,
                                              boolean shallowResolver) {
        TypeMapImpl.Builder typeMapBuilder = input.globalTypeContext().typeMapBuilder;
        InspectWithJavaParserImpl onDemandSourceInspection = new InspectWithJavaParserImpl(urls, typesForWildcardImport);
        typeMapBuilder.setInspectWithJavaParser(onDemandSourceInspection);

        // trigger the on-demand detection
        urls.entrySet().stream().sorted(Comparator.comparing(e -> e.getValue().toString())).forEach(e ->
                input.globalTypeContext().getTypeInspection(e.getKey()));

        if (!shallowResolver) {
            typeMapBuilder.makeParametersImmutable();
        }
        // phase 2: resolve methods and fields
        Resolver resolver = new Resolver(null, input.globalTypeContext(),
                input.globalTypeContext().typeMapBuilder.getE2ImmuAnnotationExpressions(), shallowResolver);
        List<SortedType> sortedPrimaryTypes = resolver.sortTypes(onDemandSourceInspection.typeContexts);
        messages.addAll(resolver.getMessageStream()
                .filter(m -> m.severity != Message.Severity.WARN || reportWarnings));
        return sortedPrimaryTypes;
    }

    private class InspectWithJavaParserImpl implements InspectWithJavaParser {
        private final Map<TypeInfo, TypeContext> typeContexts = new HashMap<>();
        private final Map<TypeInfo, URL> urls;
        private final Trie<TypeInfo> typesForWildcardImport;

        InspectWithJavaParserImpl(Map<TypeInfo, URL> urls, Trie<TypeInfo> typesForWildcardImport) {
            this.urls = urls;
            this.typesForWildcardImport = typesForWildcardImport;
        }

        @Override
        public void inspect(TypeInfo typeInfo, TypeInspectionImpl.Builder typeInspectionBuilder) {
            if (typeInspectionBuilder.getInspectionState() != TRIGGER_JAVA_PARSER) {
                return; // already done, or started
            }
            URL url = Objects.requireNonNull(urls.get(typeInfo),
                    "Cannot find URL for " + typeInfo.fullyQualifiedName + " in " + urls);
            try {
                LOGGER.info("Starting Java parser inspection of {}", url);
                typeInspectionBuilder.setInspectionState(STARTING_JAVA_PARSER);

                TypeContext inspectionTypeContext = new TypeContext(getTypeContext());

                InputStreamReader isr = new InputStreamReader(url.openStream(), configuration.inputConfiguration.sourceEncoding);
                String source = IOUtils.toString(isr);
                ParseAndInspect parseAndInspect = new ParseAndInspect(input.classPath(),
                        input.globalTypeContext().typeMapBuilder, typesForWildcardImport);
                List<TypeInfo> primaryTypes = parseAndInspect.run(inspectionTypeContext, url.toString(), source);
                primaryTypes.forEach(t -> typeContexts.put(t, inspectionTypeContext));

                typeInspectionBuilder.setInspectionState(FINISHED_JAVA_PARSER);

            } catch (RuntimeException rte) {
                LOGGER.error("Caught runtime exception parsing and inspecting URL {}", url);
                throw rte;
            } catch (IOException ioe) {
                LOGGER.error("Stopping runnable because of an IOException parsing URL {}", url);
                throw new RuntimeException(ioe);
            }
        }
    }

    private void writeAndUpload(List<SortedType> sortedPrimaryTypes) {
        Set<TypeInfo> typesToWrite = sortedPrimaryTypes.stream()
                .map(SortedType::primaryType).collect(Collectors.toSet());
        if (configuration.uploadConfiguration.upload) {
            AnnotationUploader annotationUploader = new AnnotationUploader(configuration.uploadConfiguration,
                    input.globalTypeContext().typeMapBuilder.getE2ImmuAnnotationExpressions());
            Map<String, String> map = annotationUploader.createMap(typesToWrite);
            annotationUploader.writeMap(map);
        }
        if (configuration.annotationXmlConfiguration.writeAnnotationXml) {
            try {
                AnnotationXmlWriter.write(configuration.annotationXmlConfiguration, typesToWrite);
            } catch (IOException ioe) {
                LOGGER.error("Caught ioe exception writing annotation XMLs");
                throw new RuntimeException(ioe);
            }
        }
    }

    private void runPrimaryTypeAnalyser(TypeMap typeMap, List<SortedType> sortedPrimaryTypes) {
        for (TypeMapVisitor typeMapVisitor : configuration.debugConfiguration.typeMapVisitors) {
            typeMapVisitor.visit(typeMap);
        }
        log(org.e2immu.analyser.util.Logger.LogTarget.ANALYSER, "Analysing primary types:\n{}",
                sortedPrimaryTypes.stream().map(t -> t.primaryType().fullyQualifiedName).collect(Collectors.joining("\n")));
        for (SortedType sortedType : sortedPrimaryTypes) {
            analyseSortedType(sortedType);
        }
    }

    private void analyseSortedType(SortedType sortedType) {
        PrimaryTypeAnalyser primaryTypeAnalyser = new PrimaryTypeAnalyser(null, sortedType, configuration,
                getTypeContext().getPrimitives(), getTypeContext().typeMapBuilder.getE2ImmuAnnotationExpressions());
        try {
            primaryTypeAnalyser.analyse();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while analysing type {}", sortedType.primaryType().fullyQualifiedName);
            throw rte;
        }
        try {
            primaryTypeAnalyser.write();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while writing out annotations for type {}",
                    sortedType.primaryType().fullyQualifiedName);
            throw rte;
        }
        try {
            primaryTypeAnalyser.check();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while checking type {}", sortedType.primaryType().fullyQualifiedName);
            throw rte;
        }
        try {
            primaryTypeAnalyser.makeImmutable();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while making analysis of type {} immutable",
                    sortedType.primaryType().fullyQualifiedName);
            throw rte;
        }
        messages.addAll(primaryTypeAnalyser.getMessageStream());
    }

    private void runShallowAnalyser(TypeMap typeMap, List<SortedType> sortedTypes) {

        // the following block of code ensures that primary types of the annotated APIs
        // are processed in the correct order

        List<TypeInfo> types = new LinkedList<>();
        sortedTypes.forEach(st -> types.add(st.primaryType()));
        Set<TypeInfo> alreadyIncluded = new HashSet<>(types);

        typeMap.visit(new String[0], (s, list) -> {
            for (TypeInfo typeInfo : list) {
                if (typeInfo.typeInspection.isSet() &&
                        !typeInfo.typeAnalysis.isSet() &&
                        typeInfo.shallowAnalysis() &&
                        !alreadyIncluded.contains(typeInfo)) {
                    types.add(typeInfo);
                }
            }
        });

        assert types.size() == new HashSet<>(types).size() : "Duplicates?";

        ShallowTypeAnalyser shallowTypeAnalyser = new ShallowTypeAnalyser(types, configuration,
                getTypeContext().getPrimitives(), typeMap.getE2ImmuAnnotationExpressions());
        messages.addAll(shallowTypeAnalyser.analyse());

        assert types.stream().allMatch(typeInfo -> typeInfo.typeAnalysis.isSet() &&
                typeInfo.typeInspection.get().methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                        .allMatch(methodInfo -> methodInfo.methodAnalysis.isSet())) : "All method analysis set";
    }


    // only meant to be used in tests!!
    public TypeContext getTypeContext() {
        return input.globalTypeContext();
    }

    // only meant to be used in tests!
    public ByteCodeInspector getByteCodeInspector() {
        return input.byteCodeInspector();
    }

    public Stream<Message> getMessages() {
        return messages.getMessageStream();
    }
}
