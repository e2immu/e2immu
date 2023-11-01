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
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.inspector.impl.ExpressionContextImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectorImpl;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.parser.InspectWithJavaParser;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.Resolver;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.util.Trie;
import org.e2immu.annotation.Modified;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.inspector.InspectionState.*;

public class InspectAll implements InspectWithJavaParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(InspectAll.class);

    private final Configuration configuration;
    private final TypeContext globalTypeContext;
    private final Resources classPath;
    private final Map<TypeInfo, URI> sourceFiles;
    private final TypeMap.Builder typeMapBuilder;
    private final boolean dollarTypesAreNormalTypes;
    private final AnonymousTypeCounters anonymousTypeCounters;
    private final Resolver resolver;

    record TypeData(TypeDeclaration<?> typeDeclaration,
                    TypeInspector typeInspector,
                    TypeContext typeContext) {
    }

    record CompilationUnitData(URI sourceFile,
                               CompilationUnit compilationUnit,
                               String packageName,
                               TypeContext typeContextOfFile,
                               Map<TypeInfo, TypeData> typeData) {
    }

    private final SetOnce<Map<TypeInfo, CompilationUnitData>> compilationUnits = new SetOnce<>();
    private final List<ExceptionInFile> exceptions = new ArrayList<>();

    public InspectAll(Configuration configuration,
                      TypeContext globalTypeContext,
                      Resources classPath,
                      Map<TypeInfo, URI> sourceFiles,
                      TypeMap.Builder typeMapBuilder,
                      boolean dollarTypesAreNormalTypes,
                      AnonymousTypeCounters anonymousTypeCounters,
                      Resolver resolver) {
        this.configuration = configuration;
        this.globalTypeContext = globalTypeContext;
        this.classPath = classPath;
        this.typeMapBuilder = typeMapBuilder;
        this.sourceFiles = sourceFiles;
        this.dollarTypesAreNormalTypes = dollarTypesAreNormalTypes;
        this.anonymousTypeCounters = anonymousTypeCounters;
        this.resolver = resolver;
    }

    public record ExceptionInFile(Exception exception, URI uri) {
    }

    public boolean doJavaParsing() {
        LOGGER.debug("Start parsing on {} source files", sourceFiles.size());
        Map<TypeInfo, CompilationUnitData> map = sourceFiles.values()
                .parallelStream()
                .flatMap(this::doJavaParsingHandleExceptions)
                .collect(Collectors.toUnmodifiableMap(JavaParsingResult::typeInfo, jpr -> jpr.cud));
        compilationUnits.set(map);

        LOGGER.debug("Post-processing parsed compilation units");
        postProcessParsedCompilationUnits();

        return !exceptions.isEmpty();
    }

    public Map<TypeInfo, TypeContext> typeContextsOfPrimaryTypes() {
        Set<URI> done = new HashSet<>();
        Map<TypeInfo, TypeContext> map = new HashMap<>();
        for (CompilationUnitData cud : compilationUnits.get().values()) {
            if (done.add(cud.sourceFile)) {
                for (Map.Entry<TypeInfo, TypeData> entry : cud.typeData.entrySet()) {
                    map.put(entry.getKey(), entry.getValue().typeContext());
                }
            }
        }
        return Map.copyOf(map);
    }

    /*
    Loop over all distinct compilation units.

    First, build a Trie that holds all types per package, so that we know which types are visible at package level.

    These types can be added to the type context of the file.
    At the same time, we go through the class path
    And we do imports
     */
    private void postProcessParsedCompilationUnits() {
        Trie<TypeInfo> typesAtPackageLevel = makeTrie(compilationUnits.get().keySet());
        Set<URI> done = new HashSet<>();
        for (CompilationUnitData cud : compilationUnits.get().values()) {
            if (done.add(cud.sourceFile)) {
                postProcessParsedCompilationUnit(typesAtPackageLevel, cud);
            }
        }
    }

    private void postProcessParsedCompilationUnit(Trie<TypeInfo> typesAtPackageLevel,
                                                  CompilationUnitData cud) {
        expandLeavesOfClassPath(cud.typeContextOfFile, typesAtPackageLevel, cud.packageName);
        addSourceTypesToTypeContext(cud.typeContextOfFile, cud.packageName, typesAtPackageLevel);
        processImports(cud.compilationUnit, cud.typeContextOfFile, typesAtPackageLevel, cud.packageName);
    }

    private Trie<TypeInfo> makeTrie(Iterable<TypeInfo> iterable) {
        Trie<TypeInfo> trie = new Trie<>();
        for (TypeInfo typeInfo : iterable) {
            String[] split = typeInfo.packageName().split("\\.");
            trie.add(split, typeInfo);
        }
        trie.freeze();
        return trie;
    }

    record JavaParsingResult(TypeInfo typeInfo, CompilationUnitData cud) {
    }

    private Stream<JavaParsingResult> doJavaParsingHandleExceptions(URI uri) {
        try {
            return doJavaParsing(uri);
        } catch (ParseException | IOException e) {
            exceptions.add(new ExceptionInFile(e, uri));
            return Stream.of();
        }
    }

    private Stream<JavaParsingResult> doJavaParsing(URI uri) throws ParseException, IOException {
        LOGGER.debug("Running JavaParser on source file '{}'", uri);

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
            return Stream.of();
        }

        String packageName = compilationUnit.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
        TypeContext typeContextOfFile = new TypeContext(packageName, globalTypeContext, false);

        Map<TypeInfo, TypeData> typesInUnit = new HashMap<>();
        for (TypeDeclaration<?> td : compilationUnit.getTypes()) {
            String name = td.getNameAsString();
            TypeInfo typeInfo = typeContextOfFile.typeMap.getOrCreate(packageName, name, INIT_JAVA_PARSER);
            typeContextOfFile.addToContext(typeInfo);
            TypeInspector typeInspector = new TypeInspectorImpl(typeMapBuilder, typeInfo, true,
                    dollarTypesAreNormalTypes, storeComments());
            typeInspector.recursivelyAddToTypeStore(typeMapBuilder, td, dollarTypesAreNormalTypes);
            TypeContext typeContextOfType = new TypeContext(packageName, typeContextOfFile, true);
            TypeData typeData = new TypeData(td, typeInspector, typeContextOfType);
            typesInUnit.put(typeInfo, typeData);
        }
        CompilationUnitData cud = new CompilationUnitData(uri, compilationUnit, packageName, typeContextOfFile,
                Map.copyOf(typesInUnit));
        return typesInUnit.keySet().stream().map(ti -> new JavaParsingResult(ti, cud));
    }


    private void expandLeavesOfClassPath(TypeContext typeContextOfFile, Trie<TypeInfo> trie, String packageName) {
        // add all types from the current package that we can find in the class path, but ONLY
        // if it doesn't exist already in the source path! (we don't overwrite, and don't create if not needed)

        classPath.expandLeaves(packageName, ".class", (expansion, urls) -> {
            if (!expansion[expansion.length - 1].contains("$")) {
                String fqn = fqnOfClassFile(packageName, expansion);
                TypeInfo typeInfo = loadTypeDoNotImport(fqn, trie); // no subtypes, they appear individually in the classPath
                typeContextOfFile.addToContext(typeInfo, false);
            }
        });
    }

    private void addSourceTypesToTypeContext(TypeContext typeContextOfFile, String packageName, Trie<TypeInfo> trie) {
        // add all types from the current package that we can find in the trie
        trie.visitLeaves(packageName.split("\\."), (expansion, typeInfoList) -> {
            for (TypeInfo typeInfo : typeInfoList) {
                if (typeInfo.fullyQualifiedName.equals(packageName + "." + typeInfo.simpleName)) {
                    typeContextOfFile.addToContext(typeInfo);
                }
            }
        });
    }


    private void processImports(CompilationUnit compilationUnit,
                                @Modified TypeContext typeContextOfFile,
                                Trie<TypeInfo> trie,
                                String packageName) {
        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String fullyQualified = importDeclaration.getName().asString();
            if (importDeclaration.isStatic()) {
                // fields and methods; important: we do NOT add the type itself to the type context
                if (importDeclaration.isAsterisk()) {
                    TypeInfo typeInfo = loadTypeDoNotImport(fullyQualified, trie);
                    LOGGER.debug("Add import static wildcard {}", typeInfo.fullyQualifiedName);
                    typeContextOfFile.addImportStaticWildcard(typeInfo);
                } else {
                    int dot = fullyQualified.lastIndexOf('.');
                    String typeOrSubTypeName = fullyQualified.substring(0, dot);
                    String member = fullyQualified.substring(dot + 1);
                    TypeInfo typeInfo = loadTypeDoNotImport(typeOrSubTypeName, trie);
                    LOGGER.debug("Add import static, type {}, member {}", typeInfo.fullyQualifiedName, member);
                    typeContextOfFile.addImportStatic(typeInfo, member);
                }
            } else {
                // types
                if (importDeclaration.isAsterisk()) {
                    importAsterisk(typeContextOfFile, packageName, fullyQualified, trie);
                } else {
                    TypeInfo typeInfo = loadTypeDoNotImport(fullyQualified, trie);
                    LOGGER.debug("Import of {}", fullyQualified);
                    typeContextOfFile.addImport(typeInfo, true);
                }
            }
        }
    }

    private void importAsterisk(TypeContext typeContextOfFile, String packageName, String fullyQualified,
                                Trie<TypeInfo> trie) {
        LOGGER.debug("Need to parse package {}", fullyQualified);
        if (!fullyQualified.equals(packageName)) { // would be our own package; they are already there
            // we either have a type, a subtype, or a package
            String[] fullyQualifiedSplit = fullyQualified.split("\\.");
            TypeInfo inSourceTypes = TypeMapImpl.fromTrie(trie, fullyQualifiedSplit);
            if (inSourceTypes == null) {
                // deal with package
                trie.visit(fullyQualified.split("\\."), (expansion, typeInfoList) -> {
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


    private TypeInfo loadTypeDoNotImport(String fqn, Trie<TypeInfo> trie) {
        TypeInfo inMap = typeMapBuilder.get(fqn);
        if (inMap != null) {
            InspectionState inspectionState = typeMapBuilder.getInspectionState(inMap);
            if (inspectionState != null) {
                return inMap;
            }
            // there is no associated type inspection yet
        }
        // we don't know it... so we don't know the boundary between primary and subtype
        // we can either search in the class path, or in the source path

        TypeInfo inSourceTypes = TypeMapImpl.fromTrie(trie, fqn.split("\\."));
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
        LOGGER.info("Inspecting {}", typeInfo.fullyQualifiedName);

        CompilationUnitData cud = compilationUnits.get().get(typeInfo);
        assert cud != null: "Cannot find compilation unit data for "+typeInfo.fullyQualifiedName;

        TypeData typeData = cud.typeData.get(typeInfo);
        ExpressionContext expressionContext = ExpressionContextImpl.forInspectionOfPrimaryType(resolver, typeInfo,
                typeData.typeContext, anonymousTypeCounters);
        try {
            typeInspectionBuilder.setInspectionState(STARTING_JAVA_PARSER);
            List<TypeInfo> primaryTypes = typeData.typeInspector.inspect(false, null,
                    typeData.typeDeclaration, expressionContext);
            for (TypeInfo pt : primaryTypes) {
            //    TypeInspection ti = typeData.typeContext.getTypeInspection(pt);
            //    if (cud.typeContextOfFile.isImportWildcard(pt)) {
            //        ti.subTypes().forEach(st -> cud.typeContextOfFile.addImport(st, false));
            //    }
            //    ti.subTypes().forEach(typeData.typeContext::addToContext);
            }
            typeInspectionBuilder.setInspectionState(FINISHED_JAVA_PARSER);
        } catch (RuntimeException rte) {
            LOGGER.error("Caught runtime exception inspecting type {}", typeInfo.fullyQualifiedName);
            throw rte;
        }
    }

    @Override
    public boolean storeComments() {
        return configuration.inspectorConfiguration().storeComments();
    }

    public List<ExceptionInFile> getExceptions() {
        return exceptions;
    }
}

