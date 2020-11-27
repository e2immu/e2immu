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

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public class PrimaryTypeAnalyser implements AnalyserContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrimaryTypeAnalyser.class);

    private final PatternMatcher<StatementAnalyser> patternMatcher;
    public final TypeInfo primaryType;
    public final List<Analyser> analysers;
    public final Configuration configuration;
    public final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final TypeAnalysis primaryTypeAnalysis;
    private final Map<TypeInfo, TypeAnalyser> typeAnalysers;
    private final Map<MethodInfo, MethodAnalyser> methodAnalysers;
    private final Map<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final Map<ParameterInfo, ParameterAnalyser> parameterAnalysers;
    private final Messages messages = new Messages();
    private final Primitives primitives;

    public PrimaryTypeAnalyser(@NotNull SortedType sortedType,
                               @NotNull Configuration configuration,
                               @NotNull Primitives primitives,
                               @NotNull E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.configuration = configuration;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        Objects.requireNonNull(primitives);
        patternMatcher = configuration.analyserConfiguration.newPatternMatcher();
        this.primitives = primitives;
        this.primaryType = Objects.requireNonNull(sortedType.primaryType);
        assert this.primaryType.isPrimaryType();

        // do the types first, so we can pass on a TypeAnalysis objects
        ImmutableMap.Builder<TypeInfo, TypeAnalyser> typeAnalysersBuilder = new ImmutableMap.Builder<>();
        SetOnce<TypeAnalyser> primaryTypeAnalyser = new SetOnce<>();
        sortedType.methodsFieldsSubTypes.forEach(mfs -> {
            if (mfs instanceof TypeInfo typeInfo) {
                TypeAnalyser typeAnalyser = new TypeAnalyser(typeInfo, primaryType, this);
                typeAnalysersBuilder.put(typeInfo, typeAnalyser);
                if (typeInfo == primaryType) primaryTypeAnalyser.set(typeAnalyser);
            }
        });
        typeAnalysers = typeAnalysersBuilder.build();
        this.primaryTypeAnalysis = primaryTypeAnalyser.get().typeAnalysis;

        // then methods
        // filter out those that have NOT been defined!
        ImmutableMap.Builder<ParameterInfo, ParameterAnalyser> parameterAnalysersBuilder = new ImmutableMap.Builder<>();
        ImmutableMap.Builder<MethodInfo, MethodAnalyser> methodAnalysersBuilder = new ImmutableMap.Builder<>();
        sortedType.methodsFieldsSubTypes.forEach(mfs -> {
            if (mfs instanceof MethodInfo methodInfo) {
                if (methodInfo.shallowAnalysis()) {
                    copyAnnotationsIntoMethodAnalysisProperties(methodInfo);
                } else {
                    MethodAnalyser analyser = new MethodAnalyser(methodInfo, typeAnalysers.get(methodInfo.typeInfo).typeAnalysis,
                            false, this);
                    for (ParameterAnalyser parameterAnalyser : analyser.getParameterAnalysers()) {
                        parameterAnalysersBuilder.put(parameterAnalyser.parameterInfo, parameterAnalyser);
                    }
                    methodAnalysersBuilder.put(methodInfo, analyser);
                }
            }
        });

        parameterAnalysers = parameterAnalysersBuilder.build();
        methodAnalysers = methodAnalysersBuilder.build();

        // finally fields, and wire everything together
        ImmutableMap.Builder<FieldInfo, FieldAnalyser> fieldAnalysersBuilder = new ImmutableMap.Builder<>();
        analysers = sortedType.methodsFieldsSubTypes.stream().flatMap(mfs -> {
            Analyser analyser;
            if (mfs instanceof FieldInfo fieldInfo) {
                MethodAnalyser samAnalyser = null;
                if (fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
                    FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().getFieldInitialiser();
                    MethodInfo sam = fieldInitialiser.implementationOfSingleAbstractMethod();
                    if (sam != null) {
                        samAnalyser = new MethodAnalyser(sam, typeAnalysers.get(fieldInfo.owner).typeAnalysis, true, this);
                        samAnalyser.methodAnalysis.overrides.set(overrides(sam, methodAnalysers));
                    }
                }
                TypeAnalysis ownerTypeAnalysis = typeAnalysers.get(fieldInfo.owner).typeAnalysis;
                analyser = new FieldAnalyser(fieldInfo, primaryType, ownerTypeAnalysis, samAnalyser, this);
                fieldAnalysersBuilder.put(fieldInfo, (FieldAnalyser) analyser);
                if (samAnalyser != null) {
                    return List.of(analyser, samAnalyser).stream();
                }
            } else if (mfs instanceof MethodInfo methodInfo) {
                if (methodInfo.shallowAnalysis()) {
                    return Stream.empty(); // interface method
                }
                MethodAnalyser methodAnalyser = methodAnalysers.get(mfs);
                analyser = methodAnalyser;
                methodAnalyser.methodAnalysis.overrides.set(overrides((MethodInfo) mfs, methodAnalysers));
            } else if (mfs instanceof TypeInfo) {
                analyser = typeAnalysers.get(mfs);
            } else throw new UnsupportedOperationException();
            assert analyser != null : "Cannot find analyser for " + mfs.fullyQualifiedName();
            return Stream.of(analyser);
        }).collect(Collectors.toList());
        fieldAnalysers = fieldAnalysersBuilder.build();

        // all important fields of the interface have been set.
        analysers.forEach(Analyser::initialize);
    }

    // this code is partially in the ShallowTypeAnalyser as well... TODO unify
    private void copyAnnotationsIntoMethodAnalysisProperties(MethodInfo methodInfo) {
        MethodInspection methodInspection = methodInfo.methodInspection.get();

        methodInspection.getParameters().forEach(parameterInfo -> {
            ParameterAnalysisImpl.Builder builder = new ParameterAnalysisImpl.Builder(getPrimitives(), AnalysisProvider.DEFAULT_PROVIDER, parameterInfo);
            messages.addAll(builder.fromAnnotationsIntoProperties(true,
                    parameterInfo.parameterInspection.get().getAnnotations(), e2ImmuAnnotationExpressions));
            parameterInfo.setAnalysis(builder.build());
        });

        List<ParameterAnalysis> parameterAnalyses = methodInspection.getParameters().stream()
                .map(parameterInfo -> parameterInfo.parameterAnalysis.get()).collect(Collectors.toList());

        MethodAnalysisImpl.Builder methodAnalysisBuilder = new MethodAnalysisImpl.Builder(false, getPrimitives(), AnalysisProvider.DEFAULT_PROVIDER,
                methodInfo, parameterAnalyses);

        messages.addAll(methodAnalysisBuilder.fromAnnotationsIntoProperties(true, methodInspection.getAnnotations(),
                e2ImmuAnnotationExpressions));
        methodInfo.setAnalysis(methodAnalysisBuilder.build());
    }

    @Override
    public Primitives getPrimitives() {
        return primitives;
    }

    private static Set<MethodAnalysis> overrides(MethodInfo methodInfo, Map<MethodInfo, MethodAnalyser> methodAnalysers) {
        return methodInfo.methodResolution.get().overrides()
                .stream().map(mi -> {
                    MethodAnalyser methodAnalyser = methodAnalysers.get(mi);
                    assert methodAnalyser != null || mi.methodAnalysis.isSet() : "No analysis known for " + mi.fullyQualifiedName();
                    return methodAnalyser != null ? methodAnalyser.methodAnalysis : mi.methodAnalysis.get();
                }).collect(Collectors.toSet());
    }

    public Stream<Message> getMessageStream() {
        return Stream.concat(messages.getMessageStream(), analysers.stream().flatMap(Analyser::getMessageStream));
    }

    public void check() {
        analysers.forEach(Analyser::check);
    }

    public void analyse() {
        int iteration = 0;
        AnalyserComponents.Builder<Analyser, Integer> builder = new AnalyserComponents.Builder<>();
        for (Analyser analyser : analysers) {
            builder.add(analyser, analyser::analyse);
        }
        AnalyserComponents<Analyser, Integer> analyserComponents = builder.build();

        AnalysisStatus analysisStatus = AnalysisStatus.PROGRESS;

        while (analysisStatus != AnalysisStatus.DONE) {
            log(ANALYSER, "\n******\nStarting iteration {} of the primary type analyser on {}\n******", iteration, primaryType.fullyQualifiedName);

            patternMatcher.startNewIteration();

            analysisStatus = analyserComponents.run(iteration);

            iteration++;
            if (iteration > 10) {
                logAnalysisStatuses(analyserComponents);
                throw new UnsupportedOperationException("More than 10 iterations needed for primary type " + primaryType.fullyQualifiedName + "?");
            }
        }
    }

    private void logAnalysisStatuses(AnalyserComponents<Analyser, Integer> analyserComponents) {
        LOGGER.warn("Status of analysers:\n{}", analyserComponents.details());
        for (Pair<Analyser, AnalysisStatus> pair : analyserComponents.getStatuses()) {
            if (pair.v == AnalysisStatus.DELAYS) {
                LOGGER.warn("Analyser components of {}:\n{}", pair.k.getName(), pair.k.getAnalyserComponents().details());
                if (pair.k instanceof MethodAnalyser methodAnalyser) {
                    methodAnalyser.logAnalysisStatuses();
                }
            }
        }
    }

    public void write() {
        analysers.forEach(analyser -> {
            analyser.write();
            analyser.getMember().setAnalysis(analyser.getAnalysis().build());
            if (analyser instanceof MethodAnalyser methodAnalyser) {
                methodAnalyser.getParameterAnalysers().forEach(parameterAnalyser ->
                        parameterAnalyser.parameterInfo.setAnalysis(parameterAnalyser.parameterAnalysis.build()));
            }
        });
    }

    public TypeInfo getPrimaryType() {
        return primaryType;
    }

    public PatternMatcher<StatementAnalyser> getPatternMatcher() {
        return patternMatcher;
    }

    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return e2ImmuAnnotationExpressions;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public Map<FieldInfo, FieldAnalyser> getFieldAnalysers() {
        return fieldAnalysers;
    }

    @Override
    public Map<MethodInfo, MethodAnalyser> getMethodAnalysers() {
        return methodAnalysers;
    }

    @Override
    public Map<TypeInfo, TypeAnalyser> getTypeAnalysers() {
        return typeAnalysers;
    }

    public Map<ParameterInfo, ParameterAnalyser> getParameterAnalysers() {
        return parameterAnalysers;
    }

    @Override
    public TypeAnalysis getPrimaryTypeAnalysis() {
        return primaryTypeAnalysis;
    }
}
