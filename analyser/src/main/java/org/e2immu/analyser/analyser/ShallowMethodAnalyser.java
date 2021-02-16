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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SMapList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShallowMethodAnalyser {
    private final Primitives primitives;
    private final AnalysisProvider analysisProvider;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Messages messages = new Messages();

    public ShallowMethodAnalyser(Primitives primitives,
                                 AnalysisProvider analysisProvider,
                                 E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.analysisProvider = analysisProvider;
        this.primitives = primitives;
    }


    public MethodAnalysisImpl.Builder copyAnnotationsIntoMethodAnalysisProperties(MethodInfo methodInfo) {
        Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> map = collectAnnotations(methodInfo);

        MethodInspection methodInspection = methodInfo.methodInspection.get();

        List<ParameterAnalysis> parameterAnalyses = new ArrayList<>();

        methodInspection.getParameters().forEach(parameterInfo -> {
            ParameterAnalysisImpl.Builder builder = new ParameterAnalysisImpl.Builder(primitives, analysisProvider, parameterInfo);
            messages.addAll(builder.fromAnnotationsIntoProperties(VariableProperty.NOT_NULL_PARAMETER, true, true,
                    map.getOrDefault(parameterInfo, Map.of()).keySet(), e2ImmuAnnotationExpressions));
            parameterAnalyses.add(builder); // building will take place when the method analysis is built
        });

        MethodAnalysisImpl.Builder methodAnalysisBuilder = new MethodAnalysisImpl.Builder(false, primitives, analysisProvider,
                methodInfo, parameterAnalyses);

        messages.addAll(methodAnalysisBuilder.fromAnnotationsIntoProperties(VariableProperty.NOT_NULL_EXPRESSION, false, true, map.getOrDefault(methodInfo, Map.of()).keySet(),
                e2ImmuAnnotationExpressions));
        return methodAnalysisBuilder;
    }

    private Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> collectAnnotations(MethodInfo methodInfo) {
        Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> map = new HashMap<>();

        Map<AnnotationExpression, List<MethodInfo>> methodMap = new HashMap<>();
        map.put(methodInfo, methodMap);

        Stream.concat(Stream.of(methodInfo), methodInfo.methodResolution.get(methodInfo.fullyQualifiedName).overrides().stream()).forEach(mi -> {

            MethodInspection mii = mi.methodInspection.get();
            mii.getAnnotations().forEach(annotationExpression -> SMapList.add(methodMap, annotationExpression, mi));

            mii.getParameters().forEach(parameterInfo -> {
                Map<AnnotationExpression, List<MethodInfo>> parameterMap = map.computeIfAbsent(parameterInfo, k -> new HashMap<>());
                parameterInfo.parameterInspection.get().getAnnotations().forEach(annotationExpression ->
                        SMapList.add(parameterMap, annotationExpression, mi));
            });
        });

        map.forEach(this::checkContradictions);
        return map;
    }

    private void checkContradictions(WithInspectionAndAnalysis where, Map<AnnotationExpression, List<MethodInfo>> annotations) {
        if (annotations.size() < 2) return;
        checkContradictions(where, annotations, e2ImmuAnnotationExpressions.notModified, e2ImmuAnnotationExpressions.modified);
        checkContradictions(where, annotations, e2ImmuAnnotationExpressions.notNull, e2ImmuAnnotationExpressions.nullable);
    }

    private void checkContradictions(WithInspectionAndAnalysis where, Map<AnnotationExpression, List<MethodInfo>> annotations,
                                     AnnotationExpression left, AnnotationExpression right) {
        List<MethodInfo> leftMethods = annotations.getOrDefault(left, List.of());
        List<MethodInfo> rightMethods = annotations.getOrDefault(right, List.of());
        if (!leftMethods.isEmpty() && !rightMethods.isEmpty()) {
            messages.add(Message.newMessage(new Location(where), Message.CONTRADICTING_ANNOTATIONS,
                    left + " in " + leftMethods.stream().map(mi -> mi.fullyQualifiedName).collect(Collectors.joining("; ")) +
                            "; " + right + " in " + rightMethods.stream().map(mi -> mi.fullyQualifiedName).collect(Collectors.joining("; "))));
        }
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
