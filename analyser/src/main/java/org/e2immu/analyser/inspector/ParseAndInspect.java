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

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.e2immu.analyser.inspector.impl.ExpressionContextImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectorImpl;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.parser.impl.TypeMapImpl;
import org.e2immu.analyser.resolver.impl.ResolverImpl;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.util.Trie;
import org.e2immu.annotation.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.e2immu.analyser.inspector.InspectionState.TRIGGER_BYTECODE_INSPECTION;
import static org.e2immu.analyser.inspector.InspectionState.TRIGGER_JAVA_PARSER;

public record ParseAndInspect(Resources classPath,
                              TypeMap.Builder typeMapBuilder,
                              Trie<TypeInfo> sourceTypes,
                              AnonymousTypeCounters anonymousTypeCounters,
                              boolean dollarTypesAreNormalTypes) {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseAndInspect.class);

    // NOTE: there is a bit of optimization we can do if we parse/analyse per package

    private record TypeInspectorAndTypeDeclaration(TypeInspector typeInspector, TypeDeclaration<?> typeDeclaration) {
    }

    public record RunResult(List<TypeInfo> primaryTypes, TypeContext typeContextOfFile) {
    }

    /**
     * @param globalTypeContext the global type context
     * @param fileName          for error reporting
     * @param sourceCode        the source code to parse
     * @return the list of primary types found in the source code
     */
    public RunResult run(ResolverImpl resolver,
                         TypeContext globalTypeContext,
                         String fileName, String sourceCode) throws ParseException {
        LOGGER.debug("Parsing compilation unit {}", fileName);

        JavaParser javaParser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_16));
        ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceCode);
        if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
            parseResult.getProblems().forEach(problem -> LOGGER.error("Parsing problem: {}", problem));
            throw new ParseException();
        }
        CompilationUnit compilationUnit = parseResult.getResult().get();
        if (compilationUnit.getTypes().isEmpty()) {
            LOGGER.warn("No types in compilation unit: {}", fileName);
            return new RunResult(List.of(), globalTypeContext);
        }
        String packageName = compilationUnit.getPackageDeclaration()
                .map(pd -> pd.getName().asString())
                .orElseThrow(() -> new UnsupportedOperationException("Expect package declaration in file " + fileName));

        TypeContext typeContextOfFile = new TypeContext(packageName, globalTypeContext, false);
        addSourceTypesToTypeContext(typeContextOfFile, packageName);
        expandLeavesOfClassPath(typeContextOfFile, packageName);

        /* we first add the types to the type context, so that they're all known
           we do this BEFORE importing, because of possible cyclic dependencies
           Note that in current Java, this list is of size 1
         */
        List<TypeInspectorAndTypeDeclaration> typeInspectors = new ArrayList<>();
        compilationUnit.getTypes().forEach(td -> {
            String name = td.getName().asString();
            TypeInfo typeInfo = typeContextOfFile.typeMap.getOrCreate(packageName, name, TRIGGER_JAVA_PARSER);
            typeContextOfFile.addToContext(typeInfo);
            TypeInspector typeInspector = new TypeInspectorImpl(typeMapBuilder, typeInfo, true,
                    dollarTypesAreNormalTypes, resolver.storeComments());
            typeInspector.recursivelyAddToTypeStore(typeMapBuilder, td, dollarTypesAreNormalTypes);
            typeInspectors.add(new TypeInspectorAndTypeDeclaration(typeInspector, td));
        });

        processImports(compilationUnit, typeContextOfFile, packageName);

        // this list in current Java will only contain one element, UNLESS we're processing
        // and AnnotatedAPI file with $ types

        List<TypeInfo> allPrimaryTypesInspected = new ArrayList<>();

        // we first add the types to the type context, so that they're all known
        for (TypeInspectorAndTypeDeclaration tia : typeInspectors) {
            TypeInfo typeInfo = tia.typeInspector.getTypeInfo();
            TypeContext typeContext = new TypeContext(packageName, typeContextOfFile, true);
            // add the subtypes, because record declarations can have subtype names in their parameter lists
            TypeInspection typeInspection = typeContextOfFile.getTypeInspection(typeInfo);
            typeInspection.subTypes().forEach(typeContext::addToContext);
            ExpressionContext expressionContext = ExpressionContextImpl.forInspectionOfPrimaryType(resolver, typeInfo,
                    typeContext, anonymousTypeCounters);
            try {
                List<TypeInfo> primaryTypes = tia.typeInspector.inspect(false, null,
                        tia.typeDeclaration, expressionContext);
                allPrimaryTypesInspected.addAll(primaryTypes);
                for (TypeInfo pt : primaryTypes) {
                    if (typeContextOfFile.isImportWildcard(pt)) {
                        TypeInspection ti = typeContext.getTypeInspection(pt);
                        ti.subTypes().forEach(st -> typeContextOfFile.addImport(st, false));
                    }
                }
            } catch (RuntimeException rte) {
                LOGGER.error("Caught runtime exception inspecting type {}", typeInfo.fullyQualifiedName);
                throw rte;
            }
        }

        return new RunResult(allPrimaryTypesInspected, typeContextOfFile);
    }

    private void expandLeavesOfClassPath(TypeContext typeContextOfFile, String packageName) {
        // add all types from the current package that we can find in the class path, but ONLY
        // if it doesn't exist already in the source path! (we don't overwrite, and don't create if not needed)

        classPath.expandLeaves(packageName, ".class", (expansion, urls) -> {
            if (!expansion[expansion.length - 1].contains("$")) {
                String fqn = fqnOfClassFile(packageName, expansion);
                TypeInfo typeInfo = loadTypeDoNotImport(fqn); // no subtypes, they appear individually in the classPath
                typeContextOfFile.addToContext(typeInfo, false);
            }
        });
    }

    private void addSourceTypesToTypeContext(TypeContext typeContextOfFile, String packageName) {
        // add all types from the current package that we can find in the source path
        sourceTypes.visitLeaves(packageName.split("\\."), (expansion, typeInfoList) -> {
            for (TypeInfo typeInfo : typeInfoList) {
                if (typeInfo.fullyQualifiedName.equals(packageName + "." + typeInfo.simpleName)) {
                    typeContextOfFile.addToContext(typeInfo);
                }
            }
        });
    }

    private void processImports(CompilationUnit compilationUnit,
                                @Modified TypeContext typeContextOfFile,
                                String packageName) {
        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String fullyQualified = importDeclaration.getName().asString();
            if (importDeclaration.isStatic()) {
                // fields and methods; important: we do NOT add the type itself to the type context
                if (importDeclaration.isAsterisk()) {
                    TypeInfo typeInfo = loadTypeDoNotImport(fullyQualified);
                    LOGGER.debug("Add import static wildcard {}", typeInfo.fullyQualifiedName);
                    typeContextOfFile.addImportStaticWildcard(typeInfo);
                } else {
                    int dot = fullyQualified.lastIndexOf('.');
                    String typeOrSubTypeName = fullyQualified.substring(0, dot);
                    String member = fullyQualified.substring(dot + 1);
                    TypeInfo typeInfo = loadTypeDoNotImport(typeOrSubTypeName);
                    LOGGER.debug("Add import static, type {}, member {}", typeInfo, member);
                    typeContextOfFile.addImportStatic(typeInfo, member);
                }
            } else {
                // types
                if (importDeclaration.isAsterisk()) {
                    // lower priority names (so allowOverwrite = false)
                    importAsterisk(typeContextOfFile, packageName, fullyQualified);
                } else {
                    TypeInfo typeInfo = loadTypeDoNotImport(fullyQualified);
                    LOGGER.debug("Import of {}", fullyQualified);
                    typeContextOfFile.addImport(typeInfo, true);
                    // IGNORED: typeMapBuilder.getTypeInspection(typeInfo)
                    // IGNORED: inspection.subTypes().forEach(subType -> loadTypeDoNotImport(subType.fullyQualifiedName));
                }
            }
        }
    }

    private void importAsterisk(TypeContext typeContextOfFile, String packageName, String fullyQualified) {
        LOGGER.debug("Need to parse folder {}", fullyQualified);
        if (!fullyQualified.equals(packageName)) { // would be our own package; they are already there
            // we either have a type, a subtype, or a package
            String[] fullyQualifiedSplit = fullyQualified.split("\\.");
            TypeInfo inSourceTypes = TypeMapImpl.fromTrie(sourceTypes, fullyQualifiedSplit);
            if (inSourceTypes == null) {
                // deal with package
                sourceTypes.visit(fullyQualified.split("\\."), (expansion, typeInfoList) -> {
                    for (TypeInfo typeInfo : typeInfoList) {
                        if (typeInfo.fullyQualifiedName.equals(fullyQualified + "." + typeInfo.simpleName)) {
                            typeContextOfFile.addImport(typeInfo, false);
                        }
                    }
                });
            } else {
                // we must import all subtypes, but we will do that lazily
                typeContextOfFile.addImportWildcard(inSourceTypes);
            }
            classPath.expandLeaves(fullyQualified, ".class", (expansion, urls) -> {
                String leaf = expansion[expansion.length - 1];
                if (!leaf.contains("$")) {
                    // primary type
                    String simpleName = StringUtil.stripDotClass(leaf);
                    String fqn = fullyQualified + "." + simpleName;
                    TypeInfo typeInfo = typeContextOfFile.typeMap.get(fqn);
                    if (typeInfo == null) {
                        TypeInfo newTypeInfo = typeContextOfFile.typeMap
                                .getOrCreate(fullyQualified, simpleName, TRIGGER_BYTECODE_INSPECTION);
                        LOGGER.debug("Registering inspection handler for {}", newTypeInfo.fullyQualifiedName);
                        typeContextOfFile.addImport(newTypeInfo, false);
                    } else {
                        typeContextOfFile.addImport(typeInfo, false);
                    }
                }
            });
        }
    }

    /*
    TODO IMPLEMENT SOMEWHERE
     */
    private void importSubTypesIn(TypeContext typeContext, TypeInfo typeInfo, String startingFrom) {
        LOGGER.debug("Importing sub-types of {}, starting from {}", startingFrom, typeInfo.fullyQualifiedName);
        TypeInspection inspection = typeMapBuilder.getTypeInspection(typeInfo);
        if (inspection != null) {
            if (typeInfo.fullyQualifiedName.equals(startingFrom)) {
                inspection.subTypes().forEach(st -> typeContext.addToContext(st, true));
            } else {
                // recursive call
                inspection.subTypes().stream().filter(st -> startingFrom.startsWith(st.fullyQualifiedName))
                        .forEach(st -> importSubTypesIn(typeContext, st, startingFrom));
            }
        }
    }

    private TypeInfo loadTypeDoNotImport(String fqn) {
        TypeInfo inMap = typeMapBuilder.get(fqn);
        if (inMap != null) {
            InspectionState inspectionState = typeMapBuilder().getInspectionState(inMap);
            if (inspectionState != null) {
                return inMap;
            }
            // there is no associated type inspection yet
        }
        // we don't know it... so we don't know the boundary between primary and sub-type
        // we can either search in the class path, or in the source path

        TypeInfo inSourceTypes = TypeMapImpl.fromTrie(sourceTypes, fqn.split("\\."));
        if (inSourceTypes != null) {
            return inSourceTypes;
        }

        String path = classPath.fqnToPath(fqn, ".class");
        if (path == null) {
            LOGGER.error("ERROR: Cannot find type '{}'", fqn);
            throw new NotFoundInClassPathException(fqn);
        }
        return typeMapBuilder.getOrCreateFromPath(StringUtil.stripDotClass(path), TRIGGER_BYTECODE_INSPECTION);
    }

    public static String fqnOfClassFile(String prefix, String[] suffixes) {
        String combined = prefix + "." + String.join(".", suffixes).replaceAll("\\$", ".");
        if (combined.endsWith(".class")) {
            return combined.substring(0, combined.length() - 6);
        }
        throw new UnsupportedOperationException("Expected .class or .java file, but got " + combined);
    }

}
