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

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.PrimaryTypeAnalyser;
import org.e2immu.analyser.analyser.impl.primary.PrimaryTypeAnalyserImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.GlobalAnalyserContext;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.inspector.impl.ExpressionContextImpl;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.impl.ImportantClassesImpl;
import org.e2immu.analyser.parser.impl.InspectAll;
import org.e2immu.analyser.parser.impl.TypeMapImpl;
import org.e2immu.analyser.resolver.SortedTypes;
import org.e2immu.analyser.resolver.TypeCycle;
import org.e2immu.analyser.resolver.impl.GraphIO;
import org.e2immu.analyser.resolver.impl.ResolverImpl;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Parser {
    private static final Logger LOGGER = LoggerFactory.getLogger(Parser.class);

    public final Configuration configuration;
    private final Input input;
    private final Messages annotatedAPIMessages = new Messages();
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

    // used in CodeModernizer
    public Parser(Configuration newConfiguration, Parser previousParser) throws IOException {
        this.configuration = newConfiguration;
        this.input = previousParser.input.copy(newConfiguration);
    }

    // meant for tests only!
    public void preload(String packageName) {
        Input.preload(input.globalTypeContext(), input.classPath(), packageName);
    }

    public record RunResult(SortedTypes annotatedAPISortedTypes,
                            SortedTypes sourceSortedTypes,
                            TypeMap typeMap,
                            TypeContext typeContext,
                            AnalyserContext analyserContext) {

        public Set<TypeInfo> allPrimaryTypes() {
            return Stream.concat(annotatedAPISortedTypes.primaryTypeStream(), sourceSortedTypes.primaryTypeStream())
                    .collect(Collectors.toSet());
        }

        @SuppressWarnings("unused")
        public RunResult buildTypeMap() {
            if (typeMap instanceof TypeMapImpl.Builder builder) {
                return new RunResult(annotatedAPISortedTypes, sourceSortedTypes, builder.build(),
                        typeContext, analyserContext);
            }
            return this;
        }

        @SuppressWarnings("unused")
        public RunResult writeAnalysis() {
            if (analyserContext instanceof GlobalAnalyserContext globalAnalyserContext) {
                globalAnalyserContext.writeAll();
            }
            return this;
        }
    }

    public RunResult run() {
        LOGGER.info("Starting parser.");

        // at this point, bytecode inspection has been run on the Java base packages,
        // and some of our own annotations.
        // other bytecode inspection will take place on-demand, in the background.

        // we start the inspection and resolution of AnnotatedAPIs (Java parser, but with $ classes)
        Collection<URI> annotatedAPIs = input.annotatedAPIs().values();
        SortedTypes sortedAnnotatedAPITypes;
        if (annotatedAPIs.isEmpty()) {
            sortedAnnotatedAPITypes = SortedTypes.EMPTY;
        } else {
            sortedAnnotatedAPITypes = inspectAndResolve(input.annotatedAPIs(),
                    configuration.annotatedAPIConfiguration().reportWarnings(), true);
        }

        // and the inspection and resolution of Java sources (Java parser)
        SortedTypes resolvedSourceTypes = inspectAndResolve(input.sourceURLs(), true, false);

        TypeMap typeMap;

        // finally, there is an analysis step

        GlobalAnalyserContext globalAnalyserContext;
        if (configuration.skipAnalysis()) {
            // do not build yet, others may want to continue
            typeMap = input.globalTypeContext().typeMap();
            globalAnalyserContext = null;
        } else {
            ImportantClassesImpl importantClasses = new ImportantClassesImpl(input.globalTypeContext());

            // creating the typeMap ensures that all inspections and resolutions are set.
            typeMap = input.globalTypeContext().typeMapBuilder().build();
            LOGGER.info("Type map has been built");

            globalAnalyserContext = new GlobalAnalyserContext(input.globalTypeContext(),
                    configuration, importantClasses, typeMap.getE2ImmuAnnotationExpressions());

            LOGGER.debug("AnnotatedAPI Type cycles:\n{}", sortedAnnotatedAPITypes.typeCycles().stream()
                    .map(Object::toString).collect(Collectors.joining("\n")));
            for (TypeCycle typeCycle : sortedAnnotatedAPITypes.typeCycles()) {
                runAnalyzer(globalAnalyserContext, typeCycle, true);
            }
            globalAnalyserContext.startOnDemandMode();
            globalAnalyserContext.endOfAnnotatedAPIAnalysis();
            LOGGER.info("End of Annotated API Analysis, have {} source types cycles, largest has size {}",
                    resolvedSourceTypes.typeCycles().size(),
                    resolvedSourceTypes.typeCycles().stream().mapToInt(TypeCycle::size).max().orElse(0));

            for (TypeMapVisitor typeMapVisitor : configuration.debugConfiguration().typeMapVisitors()) {
                typeMapVisitor.visit(new TypeMapVisitor.Data(typeMap, globalAnalyserContext));
            }

            for (TypeCycle typeCycle : resolvedSourceTypes.typeCycles()) {
                LOGGER.info("Analysing primary type cycle of size {}, starting with {}",
                        typeCycle.size(), typeCycle.first());

                runAnalyzer(globalAnalyserContext, typeCycle, false);
            }
        }

        return new RunResult(sortedAnnotatedAPITypes, resolvedSourceTypes, typeMap,
                input.globalTypeContext(), globalAnalyserContext);
    }

    public TypeMap.Builder inspectOnlyForTesting() {
        inspectAndResolve(input.annotatedAPIs(),
                configuration.annotatedAPIConfiguration().reportWarnings(), true);
        return input.globalTypeContext().typeMapBuilder();
    }

    public SortedTypes inspectAndResolve(Map<TypeInfo, URI> urls,
                                         boolean reportWarnings,
                                         boolean shallowResolver) {
        ResolverImpl resolver = new ResolverImpl(anonymousTypeCounters,
                input.globalTypeContext(),
                input.globalTypeContext().typeMap().getE2ImmuAnnotationExpressions(),
                shallowResolver,
                configuration.inspectorConfiguration().storeComments(),
                input.configuration().parallel());

        TypeMap.Builder typeMapBuilder = input.globalTypeContext().typeMapBuilder();
        InspectAll inspectAll = new InspectAll(configuration, input.globalTypeContext(), input.classPath(), urls,
                typeMapBuilder, configuration.annotatedAPIConfiguration().disabled(), anonymousTypeCounters, resolver);

        typeMapBuilder.setInspectWithJavaParser(inspectAll);
        if (inspectAll.doJavaParsing()) {
            List<InspectAll.ExceptionInFile> exceptions = inspectAll.getExceptions();
            for (InspectAll.ExceptionInFile exception : exceptions) {
                LOGGER.error("Caught exception {} in {}", exception.exception(), exception.uri());
            }
            throw new RuntimeException("Have " + exceptions.size() + " parse exceptions");
        }
        Set<TypeInfo> nonEmptyCompilationUnits = inspectAll.nonEmptyCompilationUnits();

        // trigger the on-demand detection
        urls.entrySet().stream()
                .filter(e -> nonEmptyCompilationUnits.contains(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> input.globalTypeContext().getTypeInspection(e.getKey()));

        if (!shallowResolver) {
            typeMapBuilder.makeParametersImmutable();
        }

        // phase 2: resolve methods and fields
        // we're sorting the types for some stability in debugging
        TreeMap<TypeInfo, ExpressionContext> expressionContexts = new TreeMap<>();
        Map<TypeInfo, TypeContext> typeContextsOfPrimaryTypes = inspectAll.typeContextsOfPrimaryTypes();
        for (Map.Entry<TypeInfo, TypeContext> e : typeContextsOfPrimaryTypes.entrySet()) {
            ExpressionContext ec = ExpressionContextImpl.forInspectionOfPrimaryType(resolver,
                    e.getKey(), e.getValue(), anonymousTypeCounters);
            expressionContexts.put(e.getKey(), ec);
        }
        SortedTypes sortedTypes = resolver.resolve(expressionContexts);
        Stream<Message> messageStream = resolver.getMessageStream()
                .filter(m -> m.message().severity != Message.Severity.WARN || reportWarnings);
        if (shallowResolver) {
            annotatedAPIMessages.addAll(messageStream);
        } else {
            messages.addAll(messageStream);
        }
        String graphDirectory = configuration.inspectorConfiguration().graphDirectory();
        if (graphDirectory != null) {
            GraphIO.dumpGraphs(new File(graphDirectory), resolver.builtTypeGraph(), resolver.builtExternalTypeGraph(),
                    resolver.builtMethodCallGraph(), input.classPath().getJarSizes());
        }
        return sortedTypes;
    }

    private void runAnalyzer(AnalyserContext analyserContext, TypeCycle typeCycle, boolean annotatedAPI) {
        PrimaryTypeAnalyser primaryTypeAnalyser = new PrimaryTypeAnalyserImpl(analyserContext, typeCycle);
        try {
            primaryTypeAnalyser.analyse();
        } catch (RuntimeException rte) {
            LOGGER.error("Caught exception analysing type {}", primaryTypeAnalyser.getName());
            throw rte;
        }
        try {
            primaryTypeAnalyser.write();
        } catch (RuntimeException rte) {
            LOGGER.error("Caught exception writing out annotations for type {}",
                    primaryTypeAnalyser.getName());
            throw rte;
        }
        try {
            primaryTypeAnalyser.check();
        } catch (RuntimeException rte) {
            LOGGER.error("Caught exception checking type {}", primaryTypeAnalyser.getName());
            throw rte;
        }
        try {
            primaryTypeAnalyser.makeImmutable();
        } catch (RuntimeException rte) {
            LOGGER.error("Caught exception making analysis of type {} immutable",
                    primaryTypeAnalyser.getName());
            throw rte;
        }
        if (annotatedAPI) {
            annotatedAPIMessages.addAll(primaryTypeAnalyser.getMessageStream());
        } else {
            messages.addAll(primaryTypeAnalyser.getMessageStream());
        }
    }

    public record ComposerData(Collection<TypeInfo> primaryTypes, TypeMap typeMap) {
    }

    public ComposerData primaryTypesForAnnotatedAPIComposing() {
        for (String packagePrefix : configuration.annotatedAPIConfiguration().writeAnnotatedAPIPackages()) {
            Input.preload(input.globalTypeContext(), input.classPath(), packagePrefix);
        }
        LOGGER.info("Building TypeMap, fixing inspections");
        TypeMap typeMap = input.globalTypeContext().typeMapBuilder().build();

        Set<TypeInfo> typesToWrite = new HashSet<>();
        // ensure that all types in the packages to write have been byte code inspected
        for (String packagePrefix : configuration.annotatedAPIConfiguration().writeAnnotatedAPIPackages()) {
            String[] packagePrefixArray = packagePrefix.split("\\.");
            boolean allowSubPackages = packagePrefix.endsWith(".");
            typeMap.visit(packagePrefixArray, (prefix, types) -> types.stream().filter(t ->
                    (allowSubPackages || t.primaryType().packageName().equals(packagePrefix)) &&
                            t.typeInspection.isSet() &&
                            t.isPrimaryType() && t.typeInspection.get().isPublic()).forEach(typesToWrite::add));
        }
        LOGGER.info("Returning composer data with {} types", typesToWrite.size());
        return new ComposerData(typesToWrite, typeMap);
    }

    // only meant to be used in tests!!
    public TypeContext getTypeContext() {
        return input.globalTypeContext();
    }

    // only meant to be used in tests!
    public ByteCodeInspector getByteCodeInspector() {
        return input.byteCodeInspector();
    }

    public Stream<Message> getMessages() {
        return messages.getMessageStream();
    }

    public Stream<Message> getAnnotatedAPIMessages() {
        return annotatedAPIMessages.getMessageStream();
    }

    public int countMessages() {
        return messages.size();
    }
}
