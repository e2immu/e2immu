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
import org.e2immu.analyser.annotationxml.AnnotationStore;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.analyser.TypeAnalyser;
import org.e2immu.analyser.upload.AnnotationUploader;
import org.e2immu.analyser.util.Resources;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;


public class Parser {
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
        return parseJavaFiles(input.getSourcePath(), input.getSourceURLs());
    }

    public List<SortedType> runAnnotatedAPIs(List<URL> annotatedAPIs) throws IOException {
        InspectAnnotatedAPIs inspectAnnotatedAPIs = new InspectAnnotatedAPIs(globalTypeContext, byteCodeInspector);
        List<TypeInfo> types = inspectAnnotatedAPIs.inspect(annotatedAPIs, configuration.inputConfiguration.sourceEncoding);
        return types.stream().map(SortedType::new).collect(Collectors.toList());
    }

    public List<SortedType> parseJavaFiles(File... files) throws IOException {
        List<URL> list = new ArrayList<>();
        for (File f : files) {
            URL url = f.toURI().toURL();
            list.add(url);
        }
        return parseJavaFiles(null, list);
    }

    private List<SortedType> parseJavaFiles(Resources sourcePath,
                                            List<URL> urls) throws IOException {
        Map<TypeInfo, TypeContext> inspectedTypesToTypeContextOfFile = new HashMap<>();
        ParseAndInspect parseAndInspect = new ParseAndInspect(byteCodeInspector, true, sourceTypeStore);
        for (URL url : urls) {
            InputStreamReader isr = new InputStreamReader(url.openStream(), configuration.inputConfiguration.sourceEncoding);
            String source = IOUtils.toString(isr);
            TypeContext inspectionTypeContext = new TypeContext(globalTypeContext);
            List<TypeInfo> types = parseAndInspect.phase1ParseAndInspect(inspectionTypeContext, url.toString(), source);
            types.forEach(t -> inspectedTypesToTypeContextOfFile.put(t, inspectionTypeContext));
        }
        return phase2ResolveAndAnalyse(inspectedTypesToTypeContextOfFile);
    }

    private List<SortedType> phase2ResolveAndAnalyse(Map<TypeInfo, TypeContext> inspectedTypesToTypeContextOfFile) {
        // phase 2: resolve methods and fields
        List<SortedType> sortedTypes = new Resolver().sortTypes(inspectedTypesToTypeContextOfFile);

        // sort the types according to dependencies
        // within each type, sort the methods
        // phase 3: analyse all the types

        TypeAnalyser typeAnalyser = new TypeAnalyser(globalTypeContext);
        for (SortedType sortedType : sortedTypes) {
            typeAnalyser.analyse(sortedType);
        }
        for (SortedType sortedType : sortedTypes) {
            typeAnalyser.check(sortedType);
        }
        if (configuration.uploadConfiguration.upload) {
            AnnotationUploader annotationUploader = new AnnotationUploader(configuration.uploadConfiguration, globalTypeContext);
            annotationUploader.add(sortedTypes);
        }
        return sortedTypes;
    }

    public TypeContext getTypeContext() {
        return globalTypeContext;
    }

    public List<Message> getMessages() {
        return globalTypeContext.getMessages();
    }
}
