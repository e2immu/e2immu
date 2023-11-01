package org.e2immu.analyser.parser.impl;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.inspector.NotFoundInClassPathException;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.TypeInspector;
import org.e2immu.analyser.inspector.impl.TypeInspectorImpl;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.parser.InspectWithJavaParser;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.util.Trie;
import org.e2immu.annotation.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.e2immu.analyser.inspector.InspectionState.TRIGGER_BYTECODE_INSPECTION;
import static org.e2immu.analyser.inspector.InspectionState.TRIGGER_JAVA_PARSER;

public class InspectAll implements InspectWithJavaParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(InspectAll.class);

    private final Configuration configuration;
    private final TypeContext globalTypeContext;
    private final Resources classPath;
    private final Map<TypeInfo, URI> sourceFiles;
    private final TypeMap.Builder typeMapBuilder;
    private final Trie<TypeInfo> sourceTypes;
    private final boolean dollarTypesAreNormalTypes;

    record TypeData(TypeDeclaration<?> typeDeclaration,
                    TypeInspector typeInspector,
                    TypeContext typeContext) {
    }

    record CompilationUnitData(URI sourceFile,
                               CompilationUnit compilationUnit,
                               Map<TypeInfo, TypeData> typeData) {
    }

    private final Map<TypeInfo, CompilationUnitData> compilationUnits = new HashMap<>();

    public InspectAll(Configuration configuration,
                      TypeContext globalTypeContext,
                      Resources classPath,
                      Map<TypeInfo, URI> sourceFiles,
                      Trie<TypeInfo> sourceTypes,
                      TypeMap.Builder typeMapBuilder,
                      boolean dollarTypesAreNormalTypes) {
        this.configuration = configuration;
        this.globalTypeContext = globalTypeContext;
        this.classPath = classPath;
        this.typeMapBuilder = typeMapBuilder;
        this.sourceTypes = sourceTypes;
        this.sourceFiles = sourceFiles;
        this.dollarTypesAreNormalTypes = dollarTypesAreNormalTypes;
    }

    public void doJavaParsing() throws ParseException {
        for (URI sourceFile : sourceFiles.values()) {
            try {
                doJavaParsing(sourceFile);
            } catch (IOException ioException) {
                throw new ParseException("Cannot open " + sourceFile);
            }
        }
    }

    private void doJavaParsing(URI uri) throws ParseException, IOException {
        LOGGER.debug("Starting Java parser inspection of source file '{}'", uri);

        InputStreamReader isr = new InputStreamReader(uri.toURL().openStream(),
                configuration.inputConfiguration().sourceEncoding());
        StringWriter sw = new StringWriter();
        isr.transferTo(sw);
        String sourceCode = sw.toString();

        JavaParser javaParser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
        ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceCode);
        if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
            parseResult.getProblems().forEach(problem -> LOGGER.error("Parsing problem: {}", problem));
            throw new ParseException();
        }
        CompilationUnit compilationUnit = parseResult.getResult().get();
        if (compilationUnit.getTypes().isEmpty()) {
            LOGGER.info("Compilation unit '{}' is empty", uri);
            return;
        }

        String packageName = compilationUnit.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
        TypeContext typeContextOfFile = new TypeContext(packageName, globalTypeContext, false);
        addSourceTypesToTypeContext(typeContextOfFile, packageName);
        expandLeavesOfClassPath(typeContextOfFile, packageName);

        Map<TypeInfo, TypeData> typesInUnit = new HashMap<>();
        for (TypeDeclaration<?> td : compilationUnit.getTypes()) {
            String name = td.getNameAsString();
            TypeInfo typeInfo = typeContextOfFile.typeMap.getOrCreate(packageName, name, TRIGGER_JAVA_PARSER);
            typeContextOfFile.addToContext(typeInfo);
            TypeInspector typeInspector = new TypeInspectorImpl(typeMapBuilder, typeInfo, true,
                    dollarTypesAreNormalTypes, storeComments());
            typeInspector.recursivelyAddToTypeStore(typeMapBuilder, td, dollarTypesAreNormalTypes);
            TypeContext typeContextOfType = new TypeContext(packageName, typeContextOfFile, true);
            TypeData typeData = new TypeData(td, typeInspector, typeContextOfType);
            typesInUnit.put(typeInfo, typeData);
        }
        CompilationUnitData cud = new CompilationUnitData(uri, compilationUnit, Map.copyOf(typesInUnit));
        typesInUnit.keySet().forEach(ti -> compilationUnits.put(ti, cud));

        processImports(compilationUnit, typeContextOfFile, packageName);
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
                    importAsterisk(typeContextOfFile, packageName, fullyQualified);
                } else {
                    TypeInfo typeInfo = loadTypeDoNotImport(fullyQualified);
                    LOGGER.debug("Import of {}", fullyQualified);
                    typeContextOfFile.addImport(typeInfo, true);
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


    private TypeInfo loadTypeDoNotImport(String fqn) {
        TypeInfo inMap = typeMapBuilder.get(fqn);
        if (inMap != null) {
            InspectionState inspectionState = typeMapBuilder.getInspectionState(inMap);
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

    @Override
    public void inspect(TypeInfo typeInfo, TypeInspection.Builder typeInspectionBuilder) throws ParseException {

    }

    @Override
    public boolean storeComments() {
        return configuration.inspectorConfiguration().storeComments();
    }
}
