/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.AnnotationType;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class ShallowTypeAnalyser implements AnalyserContext {

    private final Messages messages = new Messages();
    private final Primitives primitives;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Map<TypeInfo, TypeAnalysis> typeAnalyses;
    private final Map<MethodInfo, MethodAnalysis> methodAnalyses;

    public ShallowTypeAnalyser(List<TypeInfo> types, Primitives primitives, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.primitives = primitives;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        ImmutableMap.Builder<TypeInfo, TypeAnalysis> typeAnalysesBuilder = new ImmutableMap.Builder<>();
        ImmutableMap.Builder<MethodInfo, MethodAnalysis> methodAnalysesBuilder = new ImmutableMap.Builder<>();
        for (TypeInfo typeInfo : types) {
            typeAnalysesBuilder.put(typeInfo, new TypeAnalysisImpl.Builder(primitives, typeInfo));

            typeInfo.typeInspection.get().methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM).forEach(methodInfo -> {

                MethodInspection methodInspection = methodInfo.methodInspection.get();

                methodInspection.parameters.forEach(parameterInfo -> {
                    ParameterAnalysisImpl.Builder parameterAnalysisBuilder = new ParameterAnalysisImpl.Builder(primitives, AnalysisProvider.DEFAULT_PROVIDER, parameterInfo);
                    messages.addAll(parameterAnalysisBuilder.fromAnnotationsIntoProperties(true, true,
                            parameterInfo.parameterInspection.get().annotations, e2ImmuAnnotationExpressions));
                    parameterInfo.setAnalysis(parameterAnalysisBuilder.build());
                });
                List<ParameterAnalysis> parameterAnalyses = methodInspection.parameters.stream()
                        .map(parameterInfo -> parameterInfo.parameterAnalysis.get()).collect(Collectors.toList());

                MethodAnalysisImpl.Builder methodAnalysisBuilder = new MethodAnalysisImpl.Builder(primitives, this, methodInfo, parameterAnalyses);
                methodAnalysesBuilder.put(methodInfo, methodAnalysisBuilder);

            });
        }
        typeAnalyses = typeAnalysesBuilder.build();
        methodAnalyses = methodAnalysesBuilder.build();
    }

    @Override
    public Primitives getPrimitives() {
        return primitives;
    }

    @Override
    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return e2ImmuAnnotationExpressions;
    }

    @Override
    public Map<MethodInfo, MethodAnalyser> getMethodAnalysers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        return parameterInfo.parameterAnalysis.get();
    }

    @Override
    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalysis methodAnalysis = methodAnalyses.get(methodInfo);
        if (methodAnalysis != null) return methodAnalysis;
        return methodInfo.methodAnalysis.get();
    }

    @Override
    public Map<TypeInfo, TypeAnalyser> getTypeAnalysers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeAnalyses.get(typeInfo);
        if (typeAnalysis != null) return typeAnalysis;
        return typeInfo.typeAnalysis.get();
    }

    public Messages analyse() {
        // create method analysers
        log(ANALYSER, "Starting shallow analysis on {} types, {} methods", typeAnalyses.size(), methodAnalyses.size());

        Map<MethodInfo, MethodAnalysisImpl.Builder> buildersForCompanionAnalysis = new HashMap<>();
        typeAnalyses.forEach((typeInfo, typeAnalysis) -> {
            // do types and fields; no need to recurse into sub-types
            shallowTypeAndFieldAnalysis(typeInfo, (TypeAnalysisImpl.Builder) typeAnalysis, primitives, e2ImmuAnnotationExpressions);

            // now what's left of methods
            typeInfo.typeInspection.getPotentiallyRun().methodsAndConstructors().forEach(methodInfo -> {
                MethodAnalysisImpl.Builder methodAnalysisBuilder = (MethodAnalysisImpl.Builder) methodAnalyses.get(methodInfo);

                messages.addAll(methodAnalysisBuilder.fromAnnotationsIntoProperties(false, true,
                        methodInfo.methodInspection.get().annotations, e2ImmuAnnotationExpressions));

                if (methodInfo.methodInspection.get().companionMethods.isEmpty()) {
                    methodInfo.setAnalysis(methodAnalysisBuilder.build());
                } else {
                    buildersForCompanionAnalysis.put(methodInfo, methodAnalysisBuilder);
                }
            });
        });
        int iteration = 0;
        while (!buildersForCompanionAnalysis.isEmpty()) {
            List<MethodInfo> keysToRemove = new LinkedList<>();

            int effectivelyFinalIteration = iteration;

            buildersForCompanionAnalysis.forEach(((methodInfo, builder) -> {
                AtomicBoolean delays = new AtomicBoolean();
                methodInfo.methodInspection.get().companionMethods.forEach((cmn, companionMethod) -> {
                    if (!builder.companionAnalyses.isSet(cmn)) {
                        CompanionAnalyser companionAnalyser = new CompanionAnalyser(
                                this,
                                methodInfo.typeInfo.typeAnalysis.get(),
                                cmn, companionMethod,
                                methodInfo,
                                AnnotationType.CONTRACT);
                        AnalysisStatus analysisStatus = companionAnalyser.analyse(effectivelyFinalIteration);
                        if (analysisStatus == AnalysisStatus.DONE) {
                            CompanionAnalysis companionAnalysis = companionAnalyser.companionAnalysis.build();
                            builder.companionAnalyses.put(cmn, companionAnalysis);
                        } else {
                            log(DELAYED, "Delaying analysis of {} in {}", cmn, methodInfo.fullyQualifiedName());
                            delays.set(true);
                        }
                    }
                });

                if (!delays.get()) {
                    keysToRemove.add(methodInfo);
                    methodInfo.setAnalysis(builder.build());
                }
            }));

            if (keysToRemove.isEmpty()) {
                throw new UnsupportedOperationException("Infinite loop: could not remove keys");
            }
            buildersForCompanionAnalysis.keySet().removeAll(keysToRemove);
            log(ANALYSER, "At end of iteration {} in shallow method analysis, removed {}, remaining {}", iteration,
                    keysToRemove.size(), buildersForCompanionAnalysis.size());
            iteration++;
        }

        return messages;
    }

    private void shallowTypeAndFieldAnalysis(TypeInfo typeInfo,
                                             TypeAnalysisImpl.Builder typeAnalysisBuilder,
                                             Primitives primitives,
                                             E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        log(RESOLVE, "copy annotations into properties: {}", typeInfo.fullyQualifiedName);

        TypeInspection typeInspection = typeInfo.typeInspection.get();
        messages.addAll(typeAnalysisBuilder.fromAnnotationsIntoProperties(false, true, typeInspection.annotations, e2ImmuAnnotationExpressions));

        TypeAnalyser.findAspects(this, typeAnalysisBuilder,
                typeInspection.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM));
        typeInfo.typeAnalysis.set(typeAnalysisBuilder.build());

        typeInspection.fields.forEach(fieldInfo ->
                messages.addAll(fieldInfo.copyAnnotationsIntoFieldAnalysisProperties(primitives, e2ImmuAnnotationExpressions)));
    }

}
