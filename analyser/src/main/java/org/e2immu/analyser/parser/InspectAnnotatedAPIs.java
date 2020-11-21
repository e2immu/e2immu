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
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;


/**
 * Important: annotated API files need to be backed by .class files. This class will add annotations from
 * the annotated API files to the result of byte code inspection on the class versions.
 * This is necessary to ensure 100% compatibility with the signatures as they are in the .class versions,
 * used by the rest of the code. It allows for gaps and some creativity (e.g. different type parameter names)
 * in the annotated API files.
 * <p>
 * NOTE: Java sources do not need to be backed by .class files.
 */
public class InspectAnnotatedAPIs {
    private static final Logger LOGGER = LoggerFactory.getLogger(InspectAnnotatedAPIs.class);

    private final TypeContext globalTypeContext;
    private final TypeStore localTypeStore = new TypeMapBuilder();
    private final ByteCodeInspector byteCodeInspector;
    private final Messages messages = new Messages();

    public InspectAnnotatedAPIs(TypeContext globalTypeContext,
                                ByteCodeInspector byteCodeInspector) {
        this.globalTypeContext = globalTypeContext;
        this.byteCodeInspector = byteCodeInspector;
    }

    public List<SortedType> inspectAndResolve(Collection<URL> annotatedAPIs, Charset sourceCharSet) throws IOException {
        // load all primary types in the local type store
        // we have to do it this way, because an annotated API file may contain MULTIPLE primary types
       // for (URL url : annotatedAPIs) load(url);

        // then, inspect in the normal way using a delegating type store
        DelegatingTypeStore delegatingTypeStore = new DelegatingTypeStore(localTypeStore, globalTypeContext.typeMapBuilder);
        ParseAndInspect parseAndInspect = new ParseAndInspect(byteCodeInspector, false, localTypeStore);
        Map<TypeInfo, TypeContext> inspectedTypes = new HashMap<>();
        Map<TypeInfo, TypeContext> primarySourceTypesNotInByteCode = new HashMap<>();
        for (URL url : annotatedAPIs) {
            try (InputStreamReader isr = new InputStreamReader(url.openStream(), sourceCharSet)) {
                String source = IOUtils.toString(isr);
                TypeContext typeContextOfFile = new TypeContext(globalTypeContext, delegatingTypeStore);
                List<TypeInfo> inspectedTypesList = parseAndInspect.phase1ParseAndInspect(typeContextOfFile, url.getFile(), source);
                inspectedTypesList.forEach(typeInfo -> inspectedTypes.put(typeInfo, typeContextOfFile));
                if (inspectedTypesList.size() > 0) {
                    primarySourceTypesNotInByteCode.put(inspectedTypesList.get(0), typeContextOfFile);
                }
            }
        }

        // finally, merge the annotations in the result of .class byte code inspection
        Map<TypeInfo, TypeContext> merged = possiblyByteCodeInspectThenMerge(primarySourceTypesNotInByteCode.keySet(), inspectedTypes);
        merged.putAll(primarySourceTypesNotInByteCode);
        log(RESOLVE, "Starting resolver in {} inspected types", merged.size());
        Resolver resolver = new Resolver();
        return resolver.sortTypes(merged);
    }

    private Map<TypeInfo, TypeContext> possiblyByteCodeInspectThenMerge(Set<TypeInfo> primarySourceTypesNotInBytecode, Map<TypeInfo, TypeContext> typeToTypeContext) {
        log(INSPECT, "Starting merge with byte-code inspection");
        Map<TypeInfo, TypeContext> mergedPrimaryTypesWithTypeContext = new HashMap<>();
        localTypeStore.visit(new String[0], (s, types) -> {
            for (TypeInfo typeInfo : types) {
                if (!primarySourceTypesNotInBytecode.contains(typeInfo)) {
                    TypeInfo typeInGlobalTypeContext = globalTypeContext.getFullyQualified(typeInfo.fullyQualifiedName, false);
                    if (typeInGlobalTypeContext == null || !typeInGlobalTypeContext.hasBeenInspected()) {
                        String pathInClassPath = byteCodeInspector.getClassPath().fqnToPath(typeInfo.fullyQualifiedName, ".class");
                        byteCodeInspector.inspectFromPath(pathInClassPath);
                        typeInGlobalTypeContext = globalTypeContext.getFullyQualified(typeInfo.fullyQualifiedName, true);
                    }
                    mergeAnnotationsAndCompanions(typeInfo, typeInGlobalTypeContext);

                    ExpressionContext expressionContext = ExpressionContext.forInspectionOfPrimaryType(typeInGlobalTypeContext, globalTypeContext);
                    typeInGlobalTypeContext.resolveAllAnnotations(expressionContext);
                    if (typeInGlobalTypeContext.isPrimaryType()) {
                        mergedPrimaryTypesWithTypeContext.put(typeInGlobalTypeContext, typeToTypeContext.get(typeInfo));
                    }
                }
            }
        });
        return mergedPrimaryTypesWithTypeContext;
    }

}
