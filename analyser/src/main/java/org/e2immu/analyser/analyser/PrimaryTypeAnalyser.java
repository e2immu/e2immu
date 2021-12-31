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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Pair;
import org.e2immu.support.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.PRIMARY_TYPE_ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

/*
Recursive, but only for types inside statements, not for subtypes.

Holds either a single primary type and its subtypes, or multiple primary types,
when there is a circular dependency that cannot easily be ignored.
 */
public class PrimaryTypeAnalyser implements AnalyserContext, Analyser, HoldsAnalysers {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrimaryTypeAnalyser.class);

    private final PatternMatcher<StatementAnalyser> patternMatcher;
    public final String name;
    public final Set<TypeInfo> primaryTypes;
    public final List<Analyser> analysers;
    public final Configuration configuration;
    public final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Map<TypeInfo, TypeAnalyser> typeAnalysers;
    private final Map<MethodInfo, MethodAnalyser> methodAnalysers;
    private final Map<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final Map<ParameterInfo, ParameterAnalyser> parameterAnalysers;
    private final Messages messages = new Messages();
    private final Primitives primitives;
    private final AnalyserContext parent;
    private final Set<PrimaryTypeAnalyser> localPrimaryTypeAnalysers = new HashSet<>();

    private final AnalyserComponents<Analyser, SharedState> analyserComponents;

    record SharedState(int iteration, EvaluationContext closure) {
    }

    public PrimaryTypeAnalyser(AnalyserContext parent,
                               List<SortedType> sortedTypes,
                               Configuration configuration,
                               Primitives primitives,
                               Either<PatternMatcher<StatementAnalyser>, TypeContext> patternMatcherOrTypeContext,
                               E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.parent = parent;
        this.configuration = configuration;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        Objects.requireNonNull(primitives);
        this.patternMatcher = patternMatcherOrTypeContext.isLeft() ? patternMatcherOrTypeContext.getLeft() :
                configuration.analyserConfiguration().newPatternMatcher(patternMatcherOrTypeContext.getRight(), this);
        this.primitives = primitives;
        name = sortedTypes.stream().map(sortedType -> sortedType.primaryType().fullyQualifiedName).collect(Collectors.joining(","));
        primaryTypes = sortedTypes.stream().map(SortedType::primaryType).collect(Collectors.toUnmodifiableSet());

        assert (parent == null) == (sortedTypes.stream().allMatch(st -> st.primaryType().isPrimaryType()));

        // do the types first, so we can pass on a TypeAnalysis objects
        Map<TypeInfo, TypeAnalyser> typeAnalysersBuilder = new HashMap<>();
        sortedTypes.forEach(sortedType ->
                sortedType.methodsFieldsSubTypes().forEach(mfs -> {
                    if (mfs instanceof TypeInfo typeInfo && !typeInfo.typeAnalysis.isSet()) {
                        TypeAnalyser typeAnalyser;
                        if (typeInfo.isInterface() && (typeInfo.typeInspection.get().isSealed() || typeInfo.typeResolution.get().hasOneKnownGeneratedImplementation())) {
                            typeAnalyser = new AggregatingTypeAnalyser(typeInfo, sortedType.primaryType(), this);
                        } else {
                            typeAnalyser = new ComputingTypeAnalyser(typeInfo, sortedType.primaryType(), this);
                        }
                        typeAnalysersBuilder.put(typeInfo, typeAnalyser);
                    }
                }));
        typeAnalysers = Map.copyOf(typeAnalysersBuilder);

        // then methods
        // filtering out those methods that have not been defined is not a good idea, since the MethodAnalysisImpl object
        // can only reach TypeAnalysisImpl, and not its builder. We'd better live with empty methods in the method analyser.
        Map<ParameterInfo, ParameterAnalyser> parameterAnalysersBuilder = new HashMap<>();
        Map<MethodInfo, MethodAnalyser> methodAnalysersBuilder = new HashMap<>();
        sortedTypes.forEach(sortedType -> {
            List<WithInspectionAndAnalysis> mfss = sortedType.methodsFieldsSubTypes();
            mfss.forEach(mfs -> {
                if (mfs instanceof MethodInfo methodInfo && !methodInfo.methodAnalysis.isSet()) {
                    TypeAnalyser typeAnalyser = typeAnalysers.get(methodInfo.typeInfo);
                    MethodAnalyser methodAnalyser = MethodAnalyserFactory.create(methodInfo, typeAnalyser.typeAnalysis,
                            false, true, this);
                    for (ParameterAnalyser parameterAnalyser : methodAnalyser.getParameterAnalysers()) {
                        parameterAnalysersBuilder.put(parameterAnalyser.parameterInfo, parameterAnalyser);
                    }
                    // this has to happen before the regular analysers, because there are no delays
                    //if (methodAnalyser instanceof ShallowMethodAnalyser) {
                    //     methodAnalyser.analyse(0, null);
                    // }
                    methodAnalysersBuilder.put(methodInfo, methodAnalyser);
                    // finalizers are done early, before the first assignments
                    if (methodInfo.methodInspection.get().hasContractedFinalizer()) {
                        typeAnalyser.typeAnalysis.setProperty(Property.FINALIZER, Level.TRUE_DV);
                    }
                }
            });
        });

        parameterAnalysers = Map.copyOf(parameterAnalysersBuilder);
        methodAnalysers = Map.copyOf(methodAnalysersBuilder);

        // finally fields, and wire everything together
        Map<FieldInfo, FieldAnalyser> fieldAnalysersBuilder = new HashMap<>();
        List<Analyser> allAnalysers = sortedTypes.stream().flatMap(sortedType ->
                sortedType.methodsFieldsSubTypes().stream().flatMap(mfs -> {
                    Analyser analyser;
                    if (mfs instanceof FieldInfo fieldInfo) {
                        if (!fieldInfo.fieldAnalysis.isSet()) {
                            MethodAnalyser samAnalyser;
                            if (fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
                                FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().getFieldInitialiser();
                                MethodInfo sam = fieldInitialiser.implementationOfSingleAbstractMethod();
                                if (sam != null) {
                                    samAnalyser = Objects.requireNonNull(methodAnalysers.get(sam),
                                            "No method analyser for " + sam.fullyQualifiedName);
                                } else samAnalyser = null;
                            } else samAnalyser = null;
                            TypeAnalysis ownerTypeAnalysis = typeAnalysers.get(fieldInfo.owner).typeAnalysis;
                            analyser = new FieldAnalyser(fieldInfo, sortedType.primaryType(), ownerTypeAnalysis, samAnalyser, this);
                            fieldAnalysersBuilder.put(fieldInfo, (FieldAnalyser) analyser);
                        } else {
                            analyser = null;
                            log(PRIMARY_TYPE_ANALYSER, "Ignoring field {}, already has analysis", fieldInfo.fullyQualifiedName());
                        }
                    } else if (mfs instanceof MethodInfo) {
                        analyser = methodAnalysers.get(mfs);
                    } else if (mfs instanceof TypeInfo) {
                        analyser = typeAnalysers.get(mfs);
                    } else {
                        throw new UnsupportedOperationException("have " + mfs);
                    }
                    return analyser == null ? Stream.empty() : Stream.of(analyser);
                })).collect(Collectors.toList());
        fieldAnalysers = Map.copyOf(fieldAnalysersBuilder);

        List<MethodAnalyser> methodAnalysersInOrder = new ArrayList<>(methodAnalysers.size());
        List<FieldAnalyser> fieldAnalysersInOrder = new ArrayList<>(fieldAnalysers.size());
        List<TypeAnalyser> typeAnalysersInOrder = new ArrayList<>(typeAnalysers.size());
        allAnalysers.forEach(analyser -> {
            if (analyser instanceof MethodAnalyser ma) methodAnalysersInOrder.add(ma);
            else if (analyser instanceof TypeAnalyser ta) typeAnalysersInOrder.add(ta);
            else if (analyser instanceof FieldAnalyser fa) fieldAnalysersInOrder.add(fa);
            else throw new UnsupportedOperationException();
        });
        analysers = ListUtil.immutableConcat(methodAnalysersInOrder, fieldAnalysersInOrder, typeAnalysersInOrder);
        assert analysers.size() == new HashSet<>(analysers).size() : "There are be duplicates among the analysers?";

        // all important fields of the interface have been set.
        analysers.forEach(Analyser::initialize);

        AnalyserComponents.Builder<Analyser, SharedState> builder = new AnalyserComponents.Builder<>();
        for (Analyser analyser : analysers) {
            AnalysisStatus.AnalysisResultSupplier<SharedState> supplier = sharedState -> {
                analyser.receiveAdditionalTypeAnalysers(localPrimaryTypeAnalysers);
                AnalysisStatus status = analyser.analyse(sharedState.iteration, sharedState.closure);
                if (analyser instanceof ComputingMethodAnalyser methodAnalyser) {
                    methodAnalyser.getLocallyCreatedPrimaryTypeAnalysers().forEach(localPrimaryTypeAnalysers::add);
                }
                return status;
            };

            builder.add(analyser, supplier);
        }
        analyserComponents = builder.build();
        log(PRIMARY_TYPE_ANALYSER, "List of analysers: {}", analysers);
    }

    @Override
    public AnalyserContext getParent() {
        return parent;
    }

    @Override
    public Primitives getPrimitives() {
        return primitives;
    }

    @Override
    public Stream<Message> getMessageStream() {
        return Stream.concat(messages.getMessageStream(), analysers.stream().flatMap(Analyser::getMessageStream));
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Analysis getAnalysis() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return "PTA " + name;
    }

    @Override
    public void check() {
        analysers.forEach(Analyser::check);
    }

    public void analyse() {
        int iteration = 0;
        AnalysisStatus analysisStatus;

        do {
            log(ANALYSER, "\n******\nStarting iteration {} of the primary type analyser on {}, time {}\n******",
                    iteration, name);

            analysisStatus = analyse(iteration, null);
            iteration++;

            if (analysisStatus == AnalysisStatus.DONE) break;
        } while (analysisStatus.isProgress());
        if (analysisStatus.isDelayed()) {
            logAnalysisStatuses(analyserComponents);
            if (org.e2immu.analyser.util.Logger.isLogEnabled(org.e2immu.analyser.util.Logger.LogTarget.DELAYED)) {
                LOGGER.error("Delays: {}", analysisStatus.causesOfDelay());
            }
            throw new UnsupportedOperationException("No progress after " + iteration + " iterations for primary type(s) " + name + "?");
        }
    }

    private void logAnalysisStatuses(AnalyserComponents<Analyser, SharedState> analyserComponents) {
        LOGGER.warn("Status of analysers:\n{}", analyserComponents.details());
        for (Pair<Analyser, AnalysisStatus> pair : analyserComponents.getStatuses()) {
            if (pair.v.isDelayed()) {
                LOGGER.warn("Analyser components of {}:\n{}", pair.k.getName(), pair.k.getAnalyserComponents().details());
                if (pair.k instanceof MethodAnalyser methodAnalyser) {
                    methodAnalyser.logAnalysisStatuses();
                }
            }
        }
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initialize() {
        // nothing to be done
    }

    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        patternMatcher.startNewIteration();
        return analyserComponents.run(new SharedState(iteration, closure));
    }

    @Override
    public void write() {
        analysers.forEach(Analyser::write);
    }

    @Override
    public void makeImmutable() {
        analysers.forEach(analyser -> {
            analyser.getMember().setAnalysis(analyser.getAnalysis().build());
            if (analyser instanceof HoldsAnalysers holdsAnalysers) holdsAnalysers.makeImmutable();
        });
    }

    @Override
    public PatternMatcher<StatementAnalyser> getPatternMatcher() {
        return patternMatcher;
    }

    @Override
    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return e2ImmuAnnotationExpressions;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public FieldAnalyser getFieldAnalyser(FieldInfo fieldInfo) {
        FieldAnalyser fieldAnalyser = fieldAnalysers.get(fieldInfo);
        if (fieldAnalyser == null && parent != null) {
            return parent.getFieldAnalyser(fieldInfo);
        }
        return fieldAnalyser;
    }

    @Override
    public Stream<FieldAnalyser> fieldAnalyserStream() {
        return fieldAnalysers.values().stream();
    }

    @Override
    public MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = methodAnalysers.get(methodInfo);
        if (methodAnalyser == null && parent != null) {
            return parent.getMethodAnalyser(methodInfo);
        }
        return methodAnalyser;
    }

    @Override
    public Stream<MethodAnalyser> methodAnalyserStream() {
        return methodAnalysers.values().stream();
    }

    @Override
    public TypeAnalyser getTypeAnalyser(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = typeAnalysers.get(typeInfo);
        if (typeAnalyser == null && parent != null) {
            return parent.getTypeAnalyser(typeInfo);
        }
        return typeAnalyser;
    }

    @Override
    public ParameterAnalyser getParameterAnalyser(ParameterInfo parameterInfo) {
        ParameterAnalyser parameterAnalyser = parameterAnalysers.get(parameterInfo);
        if (parameterAnalyser == null && parent != null) {
            return parent.getParameterAnalyser(parameterInfo);
        }
        return parameterAnalyser;
    }

    @Override
    public void receiveAdditionalTypeAnalysers(Collection<PrimaryTypeAnalyser> typeAnalysers) {
        throw new UnsupportedOperationException();
    }
}
