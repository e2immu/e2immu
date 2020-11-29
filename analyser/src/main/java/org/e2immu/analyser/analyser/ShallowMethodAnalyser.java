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

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MethodInspection;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterAnalysisImpl;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;

import java.util.ArrayList;
import java.util.List;
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
        MethodInspection methodInspection = methodInfo.methodInspection.get();

        List<ParameterAnalysis> parameterAnalyses = new ArrayList<>();

        methodInspection.getParameters().forEach(parameterInfo -> {
            ParameterAnalysisImpl.Builder builder = new ParameterAnalysisImpl.Builder(primitives, analysisProvider, parameterInfo);
            messages.addAll(builder.fromAnnotationsIntoProperties(true,
                    parameterInfo.parameterInspection.get().getAnnotations(), e2ImmuAnnotationExpressions));
            parameterAnalyses.add(builder); // building will take place when the method analysis is built
        });

        MethodAnalysisImpl.Builder methodAnalysisBuilder = new MethodAnalysisImpl.Builder(false, primitives, analysisProvider,
                methodInfo, parameterAnalyses);

        messages.addAll(methodAnalysisBuilder.fromAnnotationsIntoProperties(true, methodInspection.getAnnotations(),
                e2ImmuAnnotationExpressions));
        return methodAnalysisBuilder;
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
