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
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.util.Resources;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECT;
import static org.e2immu.analyser.util.Logger.log;

public class ParseAndInspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseAndInspect.class);

    @NotNull
    private final ByteCodeInspector byteCodeInspector;
    @NotNull
    private final TypeStore sourceTypeStore;
    private final boolean hasBeenDefined;

    /**
     * @param byteCodeInspector required to inspect dependencies
     * @param hasBeenDefined    true for normal Java source code that has bodies; false for annotated API files
     * @param sourceTypeStore   required, to deal with asterisk imports in the sources
     */
    public ParseAndInspect(@NotNull ByteCodeInspector byteCodeInspector,
                           boolean hasBeenDefined,
                           @NotNull TypeStore sourceTypeStore) {
        this.byteCodeInspector = Objects.requireNonNull(byteCodeInspector);
        this.hasBeenDefined = hasBeenDefined;
        this.sourceTypeStore = Objects.requireNonNull(sourceTypeStore);
    }

    // NOTE: there is a bit of optimization we can do if we parse/analyse per package

    /**
     * @param typeContextOfFile this type context should contain a delegating type store, with <code>sourceTypeStore</code>
     *                          being the local type store
     * @param fileName          for error reporting
     * @param sourceCode        the source code to parse
     * @return the list of primary types found in the source code
     */
    public List<TypeInfo> phase1ParseAndInspect(TypeContext typeContextOfFile, String fileName, String sourceCode) {
        log(INSPECT, "Parsing compilation unit {}", fileName);

        CompilationUnit compilationUnit = StaticJavaParser.parse(sourceCode);
        if (compilationUnit.getTypes().isEmpty()) {
            LOGGER.warn("No types in compilation unit: {}", fileName);
            return List.of();
        }
        String packageName = compilationUnit.getPackageDeclaration()
                .map(pd -> pd.getName().asString())
                .orElseThrow(() -> new UnsupportedOperationException("Expect package declaration in file " + fileName));

        // add all types from the current package that we can find in the source path
        sourceTypeStore.visitLeaves(packageName.split("\\."), (expansion, typeInfoList) -> {
            for (TypeInfo typeInfo : typeInfoList) {
                if (typeInfo.fullyQualifiedName.equals(packageName + "." + typeInfo.simpleName)) {
                    typeContextOfFile.addToContext(typeInfo);
                }
            }
        });

        // add all types from the current package that we can find in the class path, but ONLY
        // if it doesn't exist already in the source path!

        // TODO: we should not add them AND inspect them; only add them with a delayed inspection runnable!
        Resources classPath = byteCodeInspector.getClassPath();
        classPath.expandLeaves(packageName, ".class", (expansion, urls) -> {
            if (!expansion[expansion.length - 1].contains("$")) {
                String fqn = fqnOfClassFile(packageName, expansion);
                TypeInfo typeInfo = typeContextOfFile.typeStore.get(fqn);
                if (typeInfo == null) {
                    TypeInfo newTypeInfo = typeContextOfFile.typeStore.getOrCreate(fqn);
                    log(INSPECT, "Registering inspection handler for {}", newTypeInfo.fullyQualifiedName);
                    newTypeInfo.typeInspection.setRunnable(() -> inspectWithByteCodeInspector(newTypeInfo));
                    typeContextOfFile.addToContext(newTypeInfo);
                }
            }
        });

        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String fullyQualified = importDeclaration.getName().asString();
            if (importDeclaration.isStatic()) {
                // fields and methods; important: we do NOT add the type itself to the type context
                if (importDeclaration.isAsterisk()) {
                    TypeInfo typeInfo = importType(fullyQualified, typeContextOfFile);
                    log(INSPECT, "Add import static wildcard {}", typeInfo.fullyQualifiedName);
                    typeContextOfFile.addImportStaticWildcard(typeInfo);
                } else {
                    int dot = fullyQualified.lastIndexOf('.');
                    String typeName = fullyQualified.substring(0, dot);
                    String member = fullyQualified.substring(dot + 1);
                    TypeInfo typeInfo = importType(typeName, typeContextOfFile);
                    log(INSPECT, "Add import static member {} on class {}", typeName, member);
                    typeContextOfFile.addImportStatic(typeInfo, member);
                }
            } else {
                // types
                if (importDeclaration.isAsterisk()) {
                    // lower priority names (so allowOverwrite = false
                    log(INSPECT, "Need to parse folder {}", fullyQualified);
                    if (!fullyQualified.equals(packageName)) { // would be our own package; they are already there
                        sourceTypeStore.visit(fullyQualified.split("\\."), (expansion, typeInfoList) -> {
                            for (TypeInfo typeInfo : typeInfoList) {
                                if (typeInfo.fullyQualifiedName.equals(fullyQualified + "." + typeInfo.simpleName)) {
                                    typeContextOfFile.addToContext(typeInfo, false);
                                }
                            }
                        });
                        classPath.expandLeaves(fullyQualified, ".class", (expansion, urls) -> {
                            if (!expansion[expansion.length - 1].contains("$")) {
                                String fqn = fqnOfClassFile(fullyQualified, expansion);
                                TypeInfo typeInfo = typeContextOfFile.getFullyQualified(fqn, false);
                                if (typeInfo == null) {
                                    TypeInfo newTypeInfo = typeContextOfFile.typeStore.getOrCreate(fqn);
                                    log(INSPECT, "Registering inspection handler for {}", newTypeInfo.fullyQualifiedName);
                                    newTypeInfo.typeInspection.setRunnable(() -> inspectWithByteCodeInspector(newTypeInfo));
                                    typeContextOfFile.addToContext(newTypeInfo, false);
                                } else {
                                    typeContextOfFile.addToContext(typeInfo, false);
                                }
                            }
                        });
                    }
                } else {
                    // higher priority names, allowOverwrite = true
                    log(INSPECT, "Import of {}", fullyQualified);
                    TypeInfo typeInfo = importType(fullyQualified, typeContextOfFile);
                    typeContextOfFile.addToContext(typeInfo, true);
                }
            }
        }
        typeContextOfFile.typeStore.visitAllNewlyCreatedTypes(typeInfo -> {
            if (!typeInfo.typeInspection.hasRunnable() &&
                    !typeInfo.typeInspection.isSetDoNotTriggerRunnable() &&
                    // this is to check that we're not talking about a subtype of a source type
                    !sourceTypeStore.containsPrefix(typeInfo.fullyQualifiedName)) {
                log(INSPECT, "Registering inspection handler for {}", typeInfo.fullyQualifiedName);
                typeInfo.typeInspection.setRunnable(() -> inspectWithByteCodeInspector(typeInfo));
            }
        });

        // we first add the types to the type context, so that they're all known
        compilationUnit.getTypes().forEach(td -> {
            String name = td.getName().asString();
            TypeInfo typeInfo = typeContextOfFile.typeStore.getOrCreate(packageName + "." + name);
            typeContextOfFile.addToContext(typeInfo);
            typeInfo.recursivelyAddToTypeStore(typeContextOfFile.typeStore, td);
        });

        // only then do we start inspection
        List<TypeInfo> result = new ArrayList<>();
        for (TypeDeclaration<?> td : compilationUnit.getTypes()) {
            String name = td.getName().asString();
            TypeInfo typeInfo = typeContextOfFile.typeStore.get(packageName + "." + name);
            // because we have a single Primitives.PRIMITIVES object, it is possible that java.lang.Object and java.lang.String
            // have already been inspected (AnnotationType as well)
            if (!typeInfo.typeInspection.isSetDoNotTriggerRunnable()) {
                try {
                    ExpressionContext expressionContext = ExpressionContext.forInspectionOfPrimaryType(typeInfo,
                            new TypeContext(packageName, typeContextOfFile));
                    typeInfo.inspect(hasBeenDefined, false, null, td, expressionContext);
                } catch (RuntimeException rte) {
                    LOGGER.error("Caught runtime exception inspecting type {}", typeInfo.fullyQualifiedName);
                    throw rte;
                }
            }
            result.add(typeInfo);
        }
        return result;
    }

    private TypeInfo importType(String fqn, TypeContext typeContext) {
        TypeInfo typeInfo = typeContext.getFullyQualified(fqn, false);
        if (typeInfo == null || !typeInfo.typeInspection.isSetDoNotTriggerRunnable() && !sourceTypeStore.containsPrefix(fqn)) {
            return inspectWithByteCodeInspector(fqn, typeContext);
        }
        return typeInfo;
    }

    // inspect from class path
    private void inspectWithByteCodeInspector(TypeInfo typeInfo) {
        String pathInClassPath = byteCodeInspector.getClassPath().fqnToPath(typeInfo.fullyQualifiedName, ".class");
        byteCodeInspector.inspectFromPath(pathInClassPath);
    }

    private TypeInfo inspectWithByteCodeInspector(String fqn, TypeContext typeContext) {
        String pathInClassPath = byteCodeInspector.getClassPath().fqnToPath(fqn, ".class");
        byteCodeInspector.inspectFromPath(pathInClassPath);
        TypeInfo typeInfo = typeContext.getFullyQualified(fqn, true);
        log(INSPECT, "Add to type context: {}", typeInfo.fullyQualifiedName);
        return typeInfo;
    }

    static String fqnOfClassFile(String prefix, String[] suffixes) {
        String combined = prefix + "." + String.join(".", suffixes).replaceAll("\\$", ".");
        if (combined.endsWith(".class")) {
            return combined.substring(0, combined.length() - 6);
        }
        throw new UnsupportedOperationException("Expected .class or .java file, but got " + combined);
    }

}
