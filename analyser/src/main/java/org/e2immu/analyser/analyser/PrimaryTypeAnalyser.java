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
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public class PrimaryTypeAnalyser implements AnalyserContext, Analyser, HoldsAnalysers {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrimaryTypeAnalyser.class);

    private final PatternMatcher<StatementAnalyser> patternMatcher;
    public final TypeInfo primaryType;
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

    private final AnalyserComponents<Analyser, SharedState> analyserComponents;

    record SharedState(int iteration, EvaluationContext closure) {
    }

    public PrimaryTypeAnalyser(AnalyserContext parent,
                               SortedType sortedType,
                               Configuration configuration,
                               Primitives primitives,
                               E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.parent = parent;
        this.configuration = configuration;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        Objects.requireNonNull(primitives);
        patternMatcher = configuration.analyserConfiguration.newPatternMatcher();
        this.primitives = primitives;
        this.primaryType = Objects.requireNonNull(sortedType.primaryType());
        assert (parent == null) == this.primaryType.isPrimaryType();

        // do the types first, so we can pass on a TypeAnalysis objects
        ImmutableMap.Builder<TypeInfo, TypeAnalyser> typeAnalysersBuilder = new ImmutableMap.Builder<>();
        SetOnce<TypeAnalyser> primaryTypeAnalyser = new SetOnce<>();
        sortedType.methodsFieldsSubTypes().forEach(mfs -> {
            if (mfs instanceof TypeInfo typeInfo) {
                TypeAnalyser typeAnalyser = new TypeAnalyser(typeInfo, primaryType, this);
                typeAnalysersBuilder.put(typeInfo, typeAnalyser);
                if (typeInfo == primaryType) primaryTypeAnalyser.set(typeAnalyser);
            }
        });
        typeAnalysers = typeAnalysersBuilder.build();

        // then methods
        // filtering out those methods that have not been defined is not a good idea, since the MethodAnalysisImpl object
        // can only reach TypeAnalysisImpl, and not its builder. We'd better live with empty methods in the method analyser.
        ImmutableMap.Builder<ParameterInfo, ParameterAnalyser> parameterAnalysersBuilder = new ImmutableMap.Builder<>();
        ImmutableMap.Builder<MethodInfo, MethodAnalyser> methodAnalysersBuilder = new ImmutableMap.Builder<>();
        sortedType.methodsFieldsSubTypes().forEach(mfs -> {
            if (mfs instanceof MethodInfo methodInfo) {
                MethodAnalyser analyser = new MethodAnalyser(methodInfo, typeAnalysers.get(methodInfo.typeInfo).typeAnalysis,
                        false, this);
                for (ParameterAnalyser parameterAnalyser : analyser.getParameterAnalysers()) {
                    parameterAnalysersBuilder.put(parameterAnalyser.parameterInfo, parameterAnalyser);
                }
                methodAnalysersBuilder.put(methodInfo, analyser);
            }
        });

        parameterAnalysers = parameterAnalysersBuilder.build();
        methodAnalysers = methodAnalysersBuilder.build();

        // finally fields, and wire everything together
        ImmutableMap.Builder<FieldInfo, FieldAnalyser> fieldAnalysersBuilder = new ImmutableMap.Builder<>();
        List<Analyser> allAnalysers = sortedType.methodsFieldsSubTypes().stream().flatMap(mfs -> {
            Analyser analyser;
            if (mfs instanceof FieldInfo fieldInfo) {
                MethodAnalyser samAnalyser = null;
                if (fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
                    FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().getFieldInitialiser();
                    MethodInfo sam = fieldInitialiser.implementationOfSingleAbstractMethod();
                    if (sam != null) {
                        samAnalyser = new MethodAnalyser(sam, typeAnalysers.get(fieldInfo.owner).typeAnalysis, true, this);
                    }
                }
                TypeAnalysis ownerTypeAnalysis = typeAnalysers.get(fieldInfo.owner).typeAnalysis;
                analyser = new FieldAnalyser(fieldInfo, primaryType, ownerTypeAnalysis, samAnalyser, this);
                fieldAnalysersBuilder.put(fieldInfo, (FieldAnalyser) analyser);
                if (samAnalyser != null) {
                    return List.of(analyser, samAnalyser).stream();
                }
            } else if (mfs instanceof MethodInfo) {
                analyser = methodAnalysers.get(mfs);
            } else if (mfs instanceof TypeInfo) {
                analyser = typeAnalysers.get(mfs);
            } else throw new UnsupportedOperationException();
            assert analyser != null : "Cannot find analyser for " + mfs.fullyQualifiedName();
            return Stream.of(analyser);
        }).collect(Collectors.toList());
        fieldAnalysers = fieldAnalysersBuilder.build();

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
        // all important fields of the interface have been set.
        analysers.forEach(Analyser::initialize);

        AnalyserComponents.Builder<Analyser, SharedState> builder = new AnalyserComponents.Builder<>();
        for (Analyser analyser : analysers) {
            builder.add(analyser, sharedState -> analyser.analyse(sharedState.iteration, sharedState.closure));
        }
        analyserComponents = builder.build();
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
        return primaryType;
    }

    @Override
    public Analysis getAnalysis() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return "PTA " + primaryType.fullyQualifiedName;
    }

    @Override
    public void check() {
        analysers.forEach(Analyser::check);
    }

    public void analyse() {
        int iteration = 0;
        AnalysisStatus analysisStatus = AnalysisStatus.PROGRESS;

        while (analysisStatus != AnalysisStatus.DONE) {
            log(ANALYSER, "\n******\nStarting iteration {} of the primary type analyser on {}\n******", iteration, primaryType.fullyQualifiedName);

            analysisStatus = analyse(iteration, null);
            iteration++;
            if (iteration > 10) {
                logAnalysisStatuses(analyserComponents);
                throw new UnsupportedOperationException("More than 10 iterations needed for primary type " + primaryType.fullyQualifiedName + "?");
            }
        }
    }

    private void logAnalysisStatuses(AnalyserComponents<Analyser, SharedState> analyserComponents) {
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
            if(analyser instanceof HoldsAnalysers holdsAnalysers) holdsAnalysers.makeImmutable();
        });
    }

    @Override
    public TypeInfo getPrimaryType() {
        return primaryType;
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
        return fieldAnalysers.get(fieldInfo);
    }

    @Override
    public Stream<FieldAnalyser> fieldAnalyserStream() {
        return fieldAnalysers.values().stream();
    }

    @Override
    public MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
        return methodAnalysers.get(methodInfo);
    }

    @Override
    public Stream<MethodAnalyser> methodAnalyserStream() {
        return methodAnalysers.values().stream();
    }

    @Override
    public TypeAnalyser getTypeAnalyser(TypeInfo typeInfo) {
        return typeAnalysers.get(typeInfo);
    }

    @Override
    public ParameterAnalyser getParameterAnalyser(ParameterInfo parameterInfo) {
        return parameterAnalysers.get(parameterInfo);
    }
}
