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

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.apache.commons.io.IOUtils;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import static org.e2immu.analyser.util.Logger.LogTarget.MERGE_ANNOTATIONS;
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
    private final TypeStore localTypeStore = new MapBasedTypeStore();
    private final ByteCodeInspector byteCodeInspector;

    public InspectAnnotatedAPIs(TypeContext globalTypeContext, ByteCodeInspector byteCodeInspector) {
        this.globalTypeContext = globalTypeContext;
        this.byteCodeInspector = byteCodeInspector;
    }

    public List<TypeInfo> inspect(List<URL> annotatedAPIs, Charset sourceCharSet) throws IOException {
        // load all primary types in the local type store
        // we have to do it this way, because an annotated API file may contain MULTIPLE primary types
        for (URL url : annotatedAPIs) load(url);

        // then, inspect in the normal way using a delegating type store
        DelegatingTypeStore delegatingTypeStore = new DelegatingTypeStore(localTypeStore, globalTypeContext.typeStore);
        ParseAndInspect parseAndInspect = new ParseAndInspect(byteCodeInspector, false, localTypeStore);
        for (URL url : annotatedAPIs) {
            try (InputStreamReader isr = new InputStreamReader(url.openStream(), sourceCharSet)) {
                String source = IOUtils.toString(isr);
                TypeContext typeContextOfFile = new TypeContext(globalTypeContext, delegatingTypeStore);
                parseAndInspect.phase1ParseAndInspect(typeContextOfFile, url.getFile(), source);
            }
        }

        // finally, merge the annotations in the result of .class byte code inspection
        return possiblyInspectThenMerge();
    }

    private List<TypeInfo> possiblyInspectThenMerge() {
        List<TypeInfo> typesInGlobalTypeContext = new LinkedList<>();
        localTypeStore.visit(new String[0], (s, types) -> {
            for (TypeInfo typeInfo : types) {
                TypeInfo typeInGlobalTypeContext = globalTypeContext.getFullyQualified(typeInfo.fullyQualifiedName, false);
                if (typeInGlobalTypeContext == null || !typeInGlobalTypeContext.hasBeenInspected()) {
                    String pathInClassPath = byteCodeInspector.getClassPath().fqnToPath(typeInfo.fullyQualifiedName, ".class");
                    byteCodeInspector.inspectFromPath(pathInClassPath);
                    typeInGlobalTypeContext = globalTypeContext.getFullyQualified(typeInfo.fullyQualifiedName, true);
                }
                typesInGlobalTypeContext.add(typeInGlobalTypeContext);
                mergeAnnotations(typeInfo, typeInGlobalTypeContext);
            }
        });
        return typesInGlobalTypeContext;
    }

    private static void mergeAnnotations(TypeInfo typeFrom, TypeInfo typeTo) {
        if (typeFrom == typeTo) return; // same object, nothing to do
        typeTo.typeInspection.overwrite(typeTo.typeInspection.get().copy(typeFrom.typeInspection.get().annotations));
        typeTo.typeInspection.get().methodsAndConstructors().forEach(methodInfo -> {
            MethodInfo methodFrom = typeFrom.getMethodOrConstructorByDistinguishingName(methodInfo.distinguishingName());
            if (methodFrom != null) {
                methodInfo.methodInspection.overwrite(methodInfo.methodInspection.get().copy(methodFrom.methodInspection.get().annotations));
                for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                    if (parameterInfo.index < methodFrom.methodInspection.get().parameters.size()) {
                        ParameterInfo parameterFrom = methodFrom.methodInspection.get().parameters.get(parameterInfo.index);
                        parameterInfo.parameterInspection.overwrite(parameterInfo.parameterInspection.get()
                                .copy(parameterFrom.parameterInspection.get().annotations));
                    } else {
                        throw new UnsupportedOperationException("Cannot have a mismatch on the number of parameters?");
                    }
                }
            } else {
                log(MERGE_ANNOTATIONS, "Method {} not found in merge", methodInfo.fullyQualifiedName());
            }
        });
        typeTo.typeInspection.get().fields.forEach(fieldInfo -> {
            FieldInfo fieldFrom = typeFrom.getFieldByName(fieldInfo.name);
            if (fieldFrom != null) {
                fieldInfo.fieldInspection.overwrite(fieldInfo.fieldInspection.get().copy(fieldFrom.fieldInspection.get().annotations));
            } else {
                log(MERGE_ANNOTATIONS, "Field {} not found in merge", fieldInfo.fullyQualifiedName());
            }
        });
    }

    void load(URL url) throws IOException {
        try (InputStreamReader isr = new InputStreamReader(url.openStream())) {
            String source = IOUtils.toString(isr);
            CompilationUnit compilationUnit = StaticJavaParser.parse(source);
            if (compilationUnit.getTypes().isEmpty()) {
                LOGGER.warn("No types in compilation unit: {}", url);
            } else {
                String packageName = compilationUnit.getPackageDeclaration()
                        .map(pd -> pd.getName().asString())
                        .orElseThrow(() -> new UnsupportedOperationException("Expect package declaration in file " + url));
                compilationUnit.getTypes().forEach(td -> {
                    String name = td.getName().asString();
                    TypeInfo typeInfo = localTypeStore.getOrCreate(packageName + "." + name);
                    typeInfo.recursivelyAddToTypeStore(localTypeStore, td);
                });
            }
        }
    }

    public TypeStore getLocalTypeStore() {
        return localTypeStore;
    }
}
