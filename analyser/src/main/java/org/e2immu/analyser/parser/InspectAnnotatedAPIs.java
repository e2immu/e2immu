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
    private final Messages messages = new Messages();
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;

    public InspectAnnotatedAPIs(TypeContext globalTypeContext,
                                E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                ByteCodeInspector byteCodeInspector) {
        this.globalTypeContext = globalTypeContext;
        this.byteCodeInspector = byteCodeInspector;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
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

                ExpressionContext expressionContext = ExpressionContext.forInspectionOfPrimaryType(typeInGlobalTypeContext, globalTypeContext);
                typeInGlobalTypeContext.resolveAllAnnotations(expressionContext);
                typeInGlobalTypeContext.copyAnnotationsIntoTypeAnalysisProperties(e2ImmuAnnotationExpressions, true, "merge annotations");
            }
        });
        return typesInGlobalTypeContext;
    }

    private static final Function<TypeInspection, List<MethodInfo>> CONSTRUCTORS = typeInspection -> typeInspection.constructors;
    private static final Function<TypeInspection, List<MethodInfo>> METHODS = typeInspection -> typeInspection.methods;

    private void mergeAnnotations(TypeInfo typeFrom, TypeInfo typeTo) {
        List<MethodInfo> extraConstructors = findMissingCheckOverride(typeFrom, typeTo, CONSTRUCTORS);
        List<MethodInfo> extraMethods = findMissingCheckOverride(typeFrom, typeTo, METHODS);

        if (typeFrom == typeTo) return; // same object, nothing to do
        typeTo.typeInspection.overwrite(typeTo.typeInspection.getPotentiallyRun().copy(typeFrom.typeInspection.getPotentiallyRun().annotations,
                extraConstructors,
                extraMethods));
        typeTo.typeInspection.getPotentiallyRun().methodsAndConstructors().forEach(methodInfo -> {
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
        typeTo.typeInspection.getPotentiallyRun().fields.forEach(fieldInfo -> {
            FieldInfo fieldFrom = typeFrom.getFieldByName(fieldInfo.name);
            if (fieldFrom != null) {
                fieldInfo.fieldInspection.overwrite(fieldInfo.fieldInspection.get().copy(fieldFrom.fieldInspection.get().annotations));
            } else {
                log(MERGE_ANNOTATIONS, "Field {} not found in merge", fieldInfo.fullyQualifiedName());
            }
        });
    }

    private List<MethodInfo> findMissingCheckOverride(
            TypeInfo typeFrom,
            TypeInfo typeTo,
            Function<TypeInspection, List<MethodInfo>> extractor) {
        List<MethodInfo> res = new ArrayList<>();
        Map<String, MethodInfo> inTypeTo = extractor.apply(typeTo.typeInspection.getPotentiallyRun())
                .stream()
                .collect(Collectors.toMap(MethodInfo::distinguishingName, mi -> mi));
        for (MethodInfo methodInfo : extractor.apply(typeFrom.typeInspection.getPotentiallyRun())) {
            String distinguishingName = methodInfo.distinguishingName();
            boolean isInTypeTo = inTypeTo.containsKey(distinguishingName);
            if (!isInTypeTo) {
                // make sure it is in one of the supertypes; otherwise, raise error
                MethodInfo copy = copy(methodInfo, typeTo);
                Set<MethodInfo> overrides = typeTo.overrides(copy, false);
                if (overrides.isEmpty()) {
                    Message error = Message.newMessage(new Location(methodInfo), Message.CANNOT_FIND_METHOD_IN_SUPER_TYPE, distinguishingName);
                    messages.add(error);
                } else {
                    log(MERGE_ANNOTATIONS, "Add copy of {}", distinguishingName);
                    res.add(copy);
                }
            }
        }
        return res;
    }

    /**
     * target type context is the globalTypeContext
     *
     * @param methodInfo the method to copy
     * @param typeTo     the type that will own the copy
     * @return the new copy
     */
    private MethodInfo copy(MethodInfo methodInfo, TypeInfo typeTo) {
        MethodInfo copy = methodInfo.isConstructor ? new MethodInfo(typeTo, List.of()) :
                new MethodInfo(typeTo, methodInfo.name, List.of(), Primitives.PRIMITIVES.voidParameterizedType,
                        methodInfo.isStatic, methodInfo.isDefaultImplementation);
        MethodInspection.MethodInspectionBuilder builder = new MethodInspection.MethodInspectionBuilder();
        MethodInspection methodInspection = methodInfo.methodInspection.get();
        TypeContext localTypeContext = new TypeContext(globalTypeContext);
        recursivelyAddTypeParameters(typeTo, localTypeContext);
        for (TypeParameter typeParameter : methodInspection.typeParameters) {
            TypeParameter newTypeParameter = new TypeParameter(copy, typeParameter.name, typeParameter.index);
            localTypeContext.addToContext(newTypeParameter);
            builder.addTypeParameter(newTypeParameter);
        }
        methodInspection.modifiers.forEach(builder::addModifier);
        methodInspection.annotations.forEach(builder::addAnnotation);
        methodInspection.parameters.forEach(parameterInfo -> {
            ParameterizedType parameterizedType = parameterInfo.parameterizedType.copy(localTypeContext);
            ParameterInfo newParameter = new ParameterInfo(copy, parameterizedType, parameterInfo.name, parameterInfo.index);
            newParameter.parameterInspection.set(new ParameterInspection.ParameterInspectionBuilder()
                    .setVarArgs(parameterInfo.parameterInspection.get().varArgs)
                    .addAnnotations(parameterInfo.parameterInspection.get().annotations)
                    .build());
            builder.addParameter(newParameter);
        });
        methodInspection.exceptionTypes.forEach(parameterizedType -> builder.addExceptionType(parameterizedType.copy(localTypeContext)));
        builder.setReturnType(methodInspection.returnType.copy(localTypeContext));
        builder.setBlock(Block.EMPTY_BLOCK);
        copy.methodInspection.set(builder.build(copy));
        return copy;
    }

    private void recursivelyAddTypeParameters(TypeInfo typeInfo, TypeContext typeContext) {
        for (TypeParameter typeParameter : typeInfo.typeInspection.getPotentiallyRun().typeParameters) {
            typeContext.addToContext(typeParameter);
        }
        if (typeInfo.typeInspection.getPotentiallyRun().packageNameOrEnclosingType.isRight()) {
            recursivelyAddTypeParameters(typeInfo.typeInspection.getPotentiallyRun().packageNameOrEnclosingType.getRight(), typeContext);
        }
    }

    void load(URL url) throws IOException {
        try (InputStreamReader isr = new InputStreamReader(url.openStream())) {
            String source = IOUtils.toString(isr);
            CompilationUnit compilationUnit;
            try {
                compilationUnit = StaticJavaParser.parse(source);
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught exception while parsing " + url);
                throw rte;
            }
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
