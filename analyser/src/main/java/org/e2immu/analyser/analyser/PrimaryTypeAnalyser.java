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
import org.e2immu.analyser.parser.SortedType;
import org.e2immu.analyser.pattern.ConditionalAssignment;
import org.e2immu.analyser.pattern.Pattern;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public class PrimaryTypeAnalyser implements AnalyserContext {

    private final PatternMatcher patternMatcher;
    public final TypeInfo primaryType;
    public final List<Analyser> analysers;
    public final Configuration configuration;
    public final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final TypeAnalysis primaryTypeAnalysis;
    private final Map<TypeInfo, TypeAnalyser> typeAnalysers;
    private final Map<MethodInfo, MethodAnalyser> methodAnalysers;
    private final Map<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final Map<ParameterInfo, ParameterAnalyser> parameterAnalysers;

    public PrimaryTypeAnalyser(SortedType sortedType, Configuration configuration, @NotNull E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.configuration = configuration;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;

        // TODO move to some other place
        Pattern pattern1 = ConditionalAssignment.pattern1();
        Pattern pattern2 = ConditionalAssignment.pattern2();
        Pattern pattern3 = ConditionalAssignment.pattern3();
        patternMatcher = new PatternMatcher(Map.of(pattern1, ConditionalAssignment.replacement1ToPattern1(pattern1),
                pattern2, ConditionalAssignment.replacement1ToPattern2(pattern2),
                pattern3, ConditionalAssignment.replacement1ToPattern3(pattern3)));


        this.primaryType = Objects.requireNonNull(sortedType.primaryType);
        assert this.primaryType.isPrimaryType();

        // do the types first, so we can pass on a TypeAnalysis objects
        ImmutableMap.Builder<TypeInfo, TypeAnalyser> typeAnalysersBuilder = new ImmutableMap.Builder<>();
        SetOnce<TypeAnalyser> primaryTypeAnalyser = new SetOnce<>();
        sortedType.types.forEach(typeInfo -> {
            TypeAnalyser typeAnalyser = new TypeAnalyser(typeInfo, primaryType, this);
            typeAnalysersBuilder.put(typeInfo, typeAnalyser);
            if (typeInfo == primaryType) primaryTypeAnalyser.set(typeAnalyser);
        });
        typeAnalysers = typeAnalysersBuilder.build();
        this.primaryTypeAnalysis = primaryTypeAnalyser.get().typeAnalysis;

        // then methods
        ImmutableMap.Builder<ParameterInfo, ParameterAnalyser> parameterAnalysersBuilder = new ImmutableMap.Builder<>();
        ImmutableMap.Builder<MethodInfo, MethodAnalyser> methodAnalysersBuilder = new ImmutableMap.Builder<>();
        sortedType.methods.forEach(methodInfo -> {
            MethodAnalyser analyser = new MethodAnalyser(methodInfo, typeAnalysers.get(methodInfo.typeInfo),
                    false, this);
            for (ParameterAnalyser parameterAnalyser : analyser.getParameterAnalysers()) {
                parameterAnalysersBuilder.put(parameterAnalyser.parameterInfo, parameterAnalyser);
            }
            methodAnalysersBuilder.put(methodInfo, analyser);
        });
        methodAnalysers = methodAnalysersBuilder.build();

        // finally fields, and wire everything together
        ImmutableMap.Builder<FieldInfo, FieldAnalyser> fieldAnalysersBuilder = new ImmutableMap.Builder<>();
        analysers = sortedType.methodsFieldsSubTypes.stream().flatMap(mfs -> {
            Analyser analyser;
            if (mfs instanceof FieldInfo) {
                FieldInfo fieldInfo = (FieldInfo) mfs;
                MethodAnalyser samAnalyser = null;
                if (fieldInfo.fieldInspection.get().initialiser.isSet()) {
                    FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().initialiser.get();
                    MethodInfo sam = fieldInitialiser.implementationOfSingleAbstractMethod;
                    if (sam != null) {
                        samAnalyser = new MethodAnalyser(sam, typeAnalysers.get(fieldInfo.owner).typeAnalysis, true, this);
                        samAnalyser.methodAnalysis.overrides.set(overrides(sam, methodAnalysers));
                    }
                }
                analyser = new FieldAnalyser(fieldInfo, primaryType, samAnalyser, this);
                fieldAnalysersBuilder.put(fieldInfo, (FieldAnalyser) analyser);
                if (samAnalyser != null) {
                    return List.of(analyser, samAnalyser).stream();
                }
            } else if (mfs instanceof MethodInfo) {
                analyser = methodAnalysers.get(mfs);
                ((MethodAnalyser) analyser).methodAnalysis.overrides.set(overrides((MethodInfo) mfs, methodAnalysers));
            } else if (mfs instanceof TypeInfo) {
                analyser = typeAnalysers.get(mfs);
            } else throw new UnsupportedOperationException();
            return List.of(analyser).stream();
        }).collect(Collectors.toList());
        fieldAnalysers = fieldAnalysersBuilder.build();
        parameterAnalysers = parameterAnalysersBuilder.build();
        // all important fields of the interface have been set.
        analysers.forEach(Analyser::initialize);
    }

    private static Set<MethodAnalysis> overrides(MethodInfo methodInfo, Map<MethodInfo, MethodAnalyser> methodAnalysers) {
        return methodInfo.typeInfo.overrides(methodInfo, true)
                .stream().map(mi -> {
                    MethodAnalyser methodAnalyser = methodAnalysers.get(mi);
                    return methodAnalyser != null ? methodAnalyser.methodAnalysis : mi.methodAnalysis.get();
                }).collect(Collectors.toSet());
    }

    public Stream<Message> getMessageStream() {
        return analysers.stream().flatMap(Analyser::getMessageStream);
    }

    public void check() {
        analysers.forEach(Analyser::check);
    }

    public void analyse() {
        boolean changes = true;
        int iteration = 0;

        while (changes) {
            log(ANALYSER, "\n******\nStarting iteration {} of the primary type analyser on {}\n******", iteration, primaryType.fullyQualifiedName);

            patternMatcher.startNewIteration();

            int finalIteration = iteration;
            changes = analysers.stream().reduce(false, (prev, analyser) -> analyser.analyse(finalIteration), (v1, v2) -> v1 || v2);

            iteration++;
            if (iteration > 10) {
                throw new UnsupportedOperationException("More than 10 iterations needed for primary type " + primaryType.fullyQualifiedName + "?");
            }
        }
    }

    public void write() {
        analysers.forEach(analyser -> {
            analyser.getMember().setAnalysis(analyser.getAnalysis());
            if (analyser instanceof MethodAnalyser) {
                MethodAnalyser methodAnalyser = (MethodAnalyser) analyser;
                methodAnalyser.getParameterAnalysers().forEach(parameterAnalyser ->
                        parameterAnalyser.parameterInfo.setAnalysis(parameterAnalyser.parameterAnalysis));
            }
        });
    }

    public TypeInfo getPrimaryType() {
        return primaryType;
    }

    public PatternMatcher getPatternMatcher() {
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
