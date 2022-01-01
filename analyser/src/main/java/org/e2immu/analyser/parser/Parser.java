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

package org.e2immu.analyser.parser;

import com.github.javaparser.ParseException;
import org.apache.commons.io.IOUtil;
import org.e2immu.analyser.analyser.impl.AnnotatedAPIAnalyser;
import org.e2immu.analyser.analyser.impl.PrimaryTypeAnalyser;
import org.e2immu.analyser.bytecode.OnDemandInspection;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.resolver.ResolverImpl;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.util.Trie;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.e2immu.support.Either;
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
    private final AnonymousTypeCounters anonymousTypeCounters = new AnonymousTypeCounters(); // anonymous class counter

    public Parser() throws IOException {
        // all the defaults will do...
        this(new Configuration.Builder().build());
    }

    public Parser(Configuration configuration) throws IOException {
        this.configuration = configuration;
        input = Input.create(configuration);
    }

    public Parser(Configuration newConfiguration, Parser previousParser) throws IOException {
        this.configuration = newConfiguration;
        this.input = Input.createNext(newConfiguration, previousParser.input.classPath(),
                previousParser.input.globalTypeContext(), previousParser.input.byteCodeInspector());
    }

    // meant for tests only!
    public void preload(String packageName) {
        Input.preload(input.globalTypeContext(), getByteCodeInspector(), input.classPath(), packageName);
    }

    public record RunResult(List<SortedType> annotatedAPISortedTypes,
                            List<SortedType> sourceSortedTypes,
                            TypeMap typeMap) {

        public Set<TypeInfo> allTypes() {
            return Stream.concat(annotatedAPISortedTypes.stream(), sourceSortedTypes.stream())
                    .map(SortedType::primaryType).collect(Collectors.toSet());
        }

    }

    public RunResult run() {
        LOGGER.info("Starting parser.");

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
                    configuration.annotatedAPIConfiguration().reportWarnings(), true);
        }

        // and the inspection and resolution of Java sources (Java parser)
        List<SortedType> resolvedSourceTypes = inspectAndResolve(input.sourceURLs(), input.sourceTypes(), true, false);

        TypeMap typeMap;

        // finally, there is an analysis step

        if (configuration.skipAnalysis()) {
            // do not build yet, others may want to continue
            typeMap = input.globalTypeContext().typeMapBuilder;
        } else {
            // creating the typeMap ensures that all inspections and resolutions are set.
            typeMap = input.globalTypeContext().typeMapBuilder.build();
            // we pass on the Java sources for the PrimaryTypeAnalyser, while all other loaded types
            // will be sent to the ShallowAnalyser
            runShallowAnalyser(typeMap, sortedAnnotatedAPITypes, resolvedSourceTypes);
            runPrimaryTypeAnalyser(typeMap, resolvedSourceTypes);
        }

        return new RunResult(sortedAnnotatedAPITypes, resolvedSourceTypes, typeMap);
    }

    public TypeMapImpl.Builder inspectOnlyForTesting() {
        inspectAndResolve(input.annotatedAPIs(), input.annotatedAPITypes(),
                configuration.annotatedAPIConfiguration().reportWarnings(), true);
        return input.globalTypeContext().typeMapBuilder;
    }

    public List<SortedType> inspectAndResolve(Map<TypeInfo, URL> urls, Trie<TypeInfo> typesForWildcardImport,
                                              boolean reportWarnings,
                                              boolean shallowResolver) {
        ResolverImpl resolver = new ResolverImpl(anonymousTypeCounters, input.globalTypeContext(),
                input.globalTypeContext().typeMapBuilder.getE2ImmuAnnotationExpressions(), shallowResolver);

        TypeMapImpl.Builder typeMapBuilder = input.globalTypeContext().typeMapBuilder;
        InspectWithJavaParserImpl onDemandSourceInspection = new InspectWithJavaParserImpl(urls, typesForWildcardImport, resolver);
        typeMapBuilder.setInspectWithJavaParser(onDemandSourceInspection);

        // trigger the on-demand detection
        urls.entrySet().stream().sorted(Comparator.comparing(e -> e.getValue().toString())).forEach(e ->
                input.globalTypeContext().getTypeInspection(e.getKey()));

        if (!shallowResolver) {
            typeMapBuilder.makeParametersImmutable();
        }

        // phase 2: resolve methods and fields
        // we're sorting the types for some stability in debugging
        TreeMap<TypeInfo, ExpressionContext> expressionContexts = new TreeMap<>();
        for (Map.Entry<TypeInfo, TypeContext> e : onDemandSourceInspection.typeContexts.entrySet()) {
            ExpressionContext ec = ExpressionContextImpl.forInspectionOfPrimaryType(resolver,
                    e.getKey(), e.getValue(), anonymousTypeCounters);
            expressionContexts.put(e.getKey(), ec);
        }
        List<SortedType> sortedPrimaryTypes = resolver.resolve(expressionContexts);
        messages.addAll(resolver.getMessageStream()
                .filter(m -> m.message().severity != Message.Severity.WARN || reportWarnings));
        return sortedPrimaryTypes;
    }

    private class InspectWithJavaParserImpl implements InspectWithJavaParser {
        private final Map<TypeInfo, TypeContext> typeContexts = new HashMap<>();
        private final Map<TypeInfo, URL> urls;
        private final Trie<TypeInfo> typesForWildcardImport;
        private final ResolverImpl resolver;

        InspectWithJavaParserImpl(Map<TypeInfo, URL> urls, Trie<TypeInfo> typesForWildcardImport, ResolverImpl resolver) {
            this.urls = urls;
            this.resolver = resolver;
            this.typesForWildcardImport = typesForWildcardImport;
        }

        @Override
        public void inspect(TypeInfo typeInfo, TypeInspectionImpl.Builder typeInspectionBuilder) throws ParseException {
            if (typeInspectionBuilder.getInspectionState() != TRIGGER_JAVA_PARSER) {
                return; // already done, or started
            }
            URL url = Objects.requireNonNull(urls.get(typeInfo),
                    "Cannot find URL for " + typeInfo.fullyQualifiedName + " in " + urls);
            try {
                LOGGER.info("Starting Java parser inspection of '{}'", url);
                typeInspectionBuilder.setInspectionState(STARTING_JAVA_PARSER);

                TypeContext inspectionTypeContext = new TypeContext(getTypeContext());

                InputStreamReader isr = new InputStreamReader(url.openStream(),
                        configuration.inputConfiguration().sourceEncoding());
                String source = IOUtil.toString(isr);
                ParseAndInspect parseAndInspect = new ParseAndInspect(input.classPath(),
                        input.globalTypeContext().typeMapBuilder, typesForWildcardImport, anonymousTypeCounters,
                        configuration.annotatedAPIConfiguration().disabled());
                List<TypeInfo> primaryTypes = parseAndInspect.run(resolver, inspectionTypeContext, url.toString(), source);
                primaryTypes.forEach(t -> typeContexts.put(t, inspectionTypeContext));

                typeInspectionBuilder.setInspectionState(FINISHED_JAVA_PARSER);

            } catch (NotFoundInClassPathException typeNotFoundException) {
                throw typeNotFoundException;
            } catch (RuntimeException rte) {
                LOGGER.error("Caught runtime exception parsing and inspecting URL '{}'", url);
                throw rte;
            } catch (IOException ioe) {
                LOGGER.error("Stopping runnable because of an IOException parsing URL '{}'", url);
                throw new RuntimeException(ioe);
            }
        }
    }

    private void runPrimaryTypeAnalyser(TypeMap typeMap, List<SortedType> sortedPrimaryTypes) {
        for (TypeMapVisitor typeMapVisitor : configuration.debugConfiguration().typeMapVisitors()) {
            typeMapVisitor.visit(typeMap);
        }
        log(org.e2immu.analyser.util.Logger.LogTarget.ANALYSER, "Analysing primary types:\n{}",
                sortedPrimaryTypes.stream().map(t -> t.primaryType().fullyQualifiedName).collect(Collectors.joining("\n")));
        List<List<SortedType>> groupByCycles = groupByCycles(sortedPrimaryTypes);
        for (List<SortedType> sortedTypeCycle : groupByCycles) {
            analyseSortedTypeCycle(sortedTypeCycle);
        }
    }

    private List<List<SortedType>> groupByCycles(List<SortedType> sortedPrimaryTypes) {
        List<List<SortedType>> cycles = new LinkedList<>();
        Set<TypeInfo> seen = new HashSet<>();
        for (SortedType sortedType : sortedPrimaryTypes) {
            if (!seen.contains(sortedType.primaryType())) {
                Set<TypeInfo> circularDependencies = sortedType.primaryType().typeResolution.get().circularDependencies();
                List<SortedType> cycle =
                        circularDependencies.isEmpty() ? List.of(sortedType) : circularDependencies.stream()
                                .sorted(Comparator.comparing(TypeInfo::fullyQualifiedName))
                                .map(typeInfo -> typeInfo.typeResolution.get().sortedType()).toList();
                cycles.add(cycle);
                seen.addAll(circularDependencies);
            }
        }
        return cycles;
    }

    private void analyseSortedTypeCycle(List<SortedType> sortedTypes) {
        PrimaryTypeAnalyser primaryTypeAnalyser = new PrimaryTypeAnalyser(null, sortedTypes, configuration,
                getTypeContext().getPrimitives(), Either.right(getTypeContext()),
                getTypeContext().typeMapBuilder.getE2ImmuAnnotationExpressions());
        try {
            primaryTypeAnalyser.analyse();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while analysing type {}", primaryTypeAnalyser.name);
            throw rte;
        }
        try {
            primaryTypeAnalyser.write();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while writing out annotations for type {}",
                    primaryTypeAnalyser.name);
            throw rte;
        }
        try {
            primaryTypeAnalyser.check();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while checking type {}", primaryTypeAnalyser.name);
            throw rte;
        }
        try {
            primaryTypeAnalyser.makeImmutable();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while making analysis of type {} immutable",
                    primaryTypeAnalyser.name);
            throw rte;
        }
        messages.addAll(primaryTypeAnalyser.getMessageStream());
    }

    private void runShallowAnalyser(TypeMap typeMap, List<SortedType> annotatedAPITypes, List<SortedType> sourceTypes) {

        // the following block of code ensures that primary types of the annotated APIs
        // are processed in the correct order

        List<TypeInfo> types = new LinkedList<>();
        annotatedAPITypes.forEach(st -> types.add(st.primaryType()));
        assert checkOnDuplicates(types);

        Set<TypeInfo> alreadyAdded = new HashSet<>(types);
        sourceTypes.forEach(st -> alreadyAdded.add(st.primaryType()));

        // all byte-code inspected types and AnnotatedAPI, excluding source types
        typeMap.visit(new String[0], (s, list) -> {
            for (TypeInfo typeInfo : list) {
                if (typeInfo.typeInspection.isSet() && !typeInfo.typeAnalysis.isSet() &&
                        typeInfo.shallowAnalysis() && !alreadyAdded.contains(typeInfo)) {
                    types.add(typeInfo);
                    alreadyAdded.add(typeInfo); // to avoid duplicates
                }
            }
        });

        assert checkOnDuplicates(types);

        AnnotatedAPIAnalyser annotatedAPIAnalyser = new AnnotatedAPIAnalyser(types, configuration,
                getTypeContext().getPrimitives(), typeMap.getE2ImmuAnnotationExpressions(), typeMap);
        messages.addAll(annotatedAPIAnalyser.analyse());

        assert types.stream()
                .filter(TypeInfo::isPublic)
                .allMatch(typeInfo -> typeInfo.typeAnalysis.isSet() &&
                        typeInfo.typeInspection.get().methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                                .filter(m -> m.methodInspection.get().isPublic())
                                .allMatch(methodInfo -> methodInfo.methodAnalysis.isSet())) : "All method analysis set";
    }

    private static boolean checkOnDuplicates(List<TypeInfo> types) {
        Set<TypeInfo> set = new HashSet<>(types);
        return types.size() == set.size();
    }

    public record ComposerData(Collection<TypeInfo> primaryTypes, TypeMap typeMap) {
    }

    public ComposerData primaryTypesForAnnotatedAPIComposing() {
        for (String packagePrefix : configuration.annotatedAPIConfiguration().writeAnnotatedAPIPackages()) {
            Input.preload(input.globalTypeContext(), input.byteCodeInspector(), input.classPath(), packagePrefix);
        }
        LOGGER.info("Building TypeMap, fixing inspections");
        TypeMap typeMap = input.globalTypeContext().typeMapBuilder.build();

        Set<TypeInfo> typesToWrite = new HashSet<>();
        // ensure that all types in the packages to write have been byte code inspected
        for (String packagePrefix : configuration.annotatedAPIConfiguration().writeAnnotatedAPIPackages()) {
            String[] packagePrefixArray = packagePrefix.split("\\.");
            boolean allowSubPackages = packagePrefix.endsWith(".");
            typeMap.visit(packagePrefixArray, (prefix, types) -> types.stream().filter(t ->
                    (allowSubPackages || t.primaryType().packageName().equals(packagePrefix)) &&
                            t.typeInspection.isSet() &&
                            t.isPrimaryType() && t.isPublic()).forEach(typesToWrite::add));
        }
        LOGGER.info("Returning composer data with {} types", typesToWrite.size());
        return new ComposerData(typesToWrite, typeMap);
    }

    // only meant to be used in tests!!
    public TypeContext getTypeContext() {
        return input.globalTypeContext();
    }

    // only meant to be used in tests!
    public OnDemandInspection getByteCodeInspector() {
        return input.byteCodeInspector();
    }

    public Stream<Message> getMessages() {
        return messages.getMessageStream();
    }

    public int countMessages() {
        return messages.size();
    }
}
