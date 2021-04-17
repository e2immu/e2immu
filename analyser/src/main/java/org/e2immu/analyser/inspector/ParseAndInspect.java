/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.inspector;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.util.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.TRIGGER_BYTECODE_INSPECTION;
import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.TRIGGER_JAVA_PARSER;
import static org.e2immu.analyser.util.Logger.LogTarget.INSPECT;
import static org.e2immu.analyser.util.Logger.log;

public record ParseAndInspect(Resources classPath,
                              TypeMapImpl.Builder typeMapBuilder,
                              Trie<TypeInfo> sourceTypes,
                              AnonymousTypeCounters anonymousTypeCounters) {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseAndInspect.class);

    // NOTE: there is a bit of optimization we can do if we parse/analyse per package

    /**
     * @param typeContextOfFile this type context should contain a delegating type store, with <code>sourceTypeStore</code>
     *                          being the local type store
     * @param fileName          for error reporting
     * @param sourceCode        the source code to parse
     * @return the list of primary types found in the source code
     */
    public List<TypeInfo> run(TypeContext typeContextOfFile, String fileName, String sourceCode) {
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
        sourceTypes.visitLeaves(packageName.split("\\."), (expansion, typeInfoList) -> {
            for (TypeInfo typeInfo : typeInfoList) {
                if (typeInfo.fullyQualifiedName.equals(packageName + "." + typeInfo.simpleName)) {
                    typeContextOfFile.addToContext(typeInfo);
                }
            }
        });

        // add all types from the current package that we can find in the class path, but ONLY
        // if it doesn't exist already in the source path! (we don't overwrite, and don't create if not needed)

        classPath.expandLeaves(packageName, ".class", (expansion, urls) -> {
            if (!expansion[expansion.length - 1].contains("$")) {
                String fqn = fqnOfClassFile(packageName, expansion);
                TypeInfo typeInfo = importTypeNoSubTypes(fqn); // no subtypes, they appear individually in the classPath
                typeContextOfFile.addToContext(typeInfo, false);
            }
        });

        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String fullyQualified = importDeclaration.getName().asString();
            if (importDeclaration.isStatic()) {
                // fields and methods; important: we do NOT add the type itself to the type context
                if (importDeclaration.isAsterisk()) {
                    TypeInfo typeInfo = importTypeNoSubTypes(fullyQualified);
                    log(INSPECT, "Add import static wildcard {}", typeInfo.fullyQualifiedName);
                    typeContextOfFile.addImportStaticWildcard(typeInfo);
                } else {
                    int dot = fullyQualified.lastIndexOf('.');
                    String typeName = fullyQualified.substring(0, dot);
                    String member = fullyQualified.substring(dot + 1);
                    TypeInfo typeInfo = importTypeNoSubTypes(typeName);
                    log(INSPECT, "Add import static member {} on class {}", typeName, member);
                    typeContextOfFile.addImportStatic(typeInfo, member);
                }
            } else {
                // types
                if (importDeclaration.isAsterisk()) {
                    // lower priority names (so allowOverwrite = false
                    log(INSPECT, "Need to parse folder {}", fullyQualified);
                    if (!fullyQualified.equals(packageName)) { // would be our own package; they are already there
                        sourceTypes.visit(fullyQualified.split("\\."), (expansion, typeInfoList) -> {
                            for (TypeInfo typeInfo : typeInfoList) {
                                if (typeInfo.fullyQualifiedName.equals(fullyQualified + "." + typeInfo.simpleName)) {
                                    typeContextOfFile.addToContext(typeInfo, false);
                                }
                            }
                        });
                        classPath.expandLeaves(fullyQualified, ".class", (expansion, urls) -> {
                            String leaf = expansion[expansion.length - 1];
                            if (!leaf.contains("$")) {
                                // primary type
                                String simpleName = StringUtil.stripDotClass(leaf);
                                String fqn = fullyQualified + "." + simpleName;
                                TypeInfo typeInfo = typeContextOfFile.typeMapBuilder.get(fqn);
                                if (typeInfo == null) {
                                    TypeInfo newTypeInfo = typeContextOfFile.typeMapBuilder
                                            .getOrCreate(fullyQualified, simpleName, TRIGGER_BYTECODE_INSPECTION);
                                    log(INSPECT, "Registering inspection handler for {}", newTypeInfo.fullyQualifiedName);
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
                    List<TypeInfo> types = importType(fullyQualified);
                    types.forEach(typeInfo -> typeContextOfFile.addToContext(typeInfo, true));
                }
            }
        }

        // this list in current Java will only contain one element, UNLESS we're processing
        // and AnnotatedAPI file with $ types

        List<TypeInfo> allPrimaryTypesInspected = new ArrayList<>();

        // we first add the types to the type context, so that they're all known
        compilationUnit.getTypes().forEach(td -> {
            String name = td.getName().asString();
            TypeInfo typeInfo = typeContextOfFile.typeMapBuilder.getOrCreate(packageName, name, TRIGGER_JAVA_PARSER);
            typeContextOfFile.addToContext(typeInfo);
            TypeInspector typeInspector = new TypeInspector(typeMapBuilder, typeInfo, true);
            typeInspector.recursivelyAddToTypeStore(typeMapBuilder, td);
            ExpressionContext expressionContext = ExpressionContext.forInspectionOfPrimaryType(typeInfo,
                    new TypeContext(packageName, typeContextOfFile), anonymousTypeCounters);
            try {
                List<TypeInfo> primaryTypes = typeInspector.inspect(false, null, td, expressionContext);
                allPrimaryTypesInspected.addAll(primaryTypes);
            } catch (RuntimeException rte) {
                LOGGER.error("Caught runtime exception inspecting type {}", typeInfo.fullyQualifiedName);
                throw rte;
            }
        });

        return allPrimaryTypesInspected;
    }

    private TypeInfo importTypeNoSubTypes(String fqn) {
        TypeInfo inMap = typeMapBuilder.get(fqn);
        if (inMap != null) return inMap;
        // we don't know it... so we don't know the boundary between primary and sub-type
        // we can either search in the class path, or in the source path

        TypeInfo inSourceTypes = TypeMapImpl.fromTrie(sourceTypes, fqn.split("\\."));
        if (inSourceTypes != null) return inSourceTypes;

        String path = classPath.fqnToPath(fqn, ".class");
        if (path == null) {
            LOGGER.error("ERROR: Cannot find type '{}'", fqn);
            throw new TypeNotFoundException(fqn);
        }
        return typeMapBuilder.getOrCreateFromPath(StringUtil.stripDotClass(path), TRIGGER_BYTECODE_INSPECTION);
    }

    /*
    when a type is imported, its sub-types are accessible straight away (they might need disambiguation, but that's not
    the problem here)
     */
    private List<TypeInfo> importType(String fqn) {
        TypeInfo typeInfo = importTypeNoSubTypes(fqn);
        TypeInspection inspection = typeMapBuilder.getTypeInspection(typeInfo);
        if (inspection != null) {
            return ListUtil.concatImmutable(List.of(typeInfo), inspection.subTypes());
        }
        return List.of(typeInfo);
    }


    public static String fqnOfClassFile(String prefix, String[] suffixes) {
        String combined = prefix + "." + String.join(".", suffixes).replaceAll("\\$", ".");
        if (combined.endsWith(".class")) {
            return combined.substring(0, combined.length() - 6);
        }
        throw new UnsupportedOperationException("Expected .class or .java file, but got " + combined);
    }

}
