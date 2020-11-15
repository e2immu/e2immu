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
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Pair;
import org.e2immu.annotation.AnnotationType;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class ShallowTypeAnalyser {

    public static Messages analyse(List<TypeInfo> types, Primitives primitives, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        Messages messages = new Messages();

        Map<MethodInfo, MethodAnalysisImpl.Builder> builders = new HashMap<>();
        for (TypeInfo typeInfo : types) {

            // do types and fields; no need to recurse into sub-types
            messages.addAll(shallowTypeAndFieldAnalysis(typeInfo, primitives, e2ImmuAnnotationExpressions));

            typeInfo.typeInspection.getPotentiallyRun().methodsAndConstructors().forEach(methodInfo -> {
                Pair<Messages, MethodAnalysisImpl.Builder> pair = methodInfo.copyAnnotationsIntoMethodAnalysisProperties(primitives, e2ImmuAnnotationExpressions);
                messages.addAll(pair.k);

                if (methodInfo.methodInspection.get().companionMethods.isEmpty()) {
                    methodInfo.setAnalysis(pair.v.build());
                } else {
                    builders.put(methodInfo, pair.v);
                }
            });
        }
        int iteration = 0;
        while (!builders.isEmpty()) {
            List<MethodInfo> keysToRemove = new LinkedList<>();

            int effectivelyFinalIteration = iteration;

            builders.forEach(((methodInfo, builder) -> {
                AtomicBoolean delays = new AtomicBoolean();
                methodInfo.methodInspection.get().companionMethods.forEach((cmn, companionMethod) -> {
                    if (!builder.companionAnalyses.isSet(cmn)) {
                        CompanionAnalyser companionAnalyser = new CompanionAnalyser(methodInfo.typeInfo.typeAnalysis.get(),
                                cmn, companionMethod, methodInfo, AnnotationType.CONTRACT);
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
            builders.keySet().removeAll(keysToRemove);
            log(ANALYSER, "At end of iteration {} in shallow method analysis, removed {}, remaining {}", iteration,
                    keysToRemove.size(), builders.size());
            iteration++;
        }

        return messages;
    }

    private static Messages shallowTypeAndFieldAnalysis(TypeInfo typeInfo, Primitives primitives, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        assert typeInfo.doesNotNeedAnalysing();
        Messages messages = new Messages();
        log(RESOLVE, "copy annotations into properties: {}", typeInfo.fullyQualifiedName);

        TypeInspection typeInspection = typeInfo.typeInspection.getPotentiallyRun();
        TypeAnalysisImpl.Builder builder = new TypeAnalysisImpl.Builder(primitives, typeInfo);
        messages.addAll(builder.fromAnnotationsIntoProperties(false, true, typeInspection.annotations, e2ImmuAnnotationExpressions));

        typeInfo.typeAnalysis.set(builder.build());

        typeInspection.fields.forEach(fieldInfo ->
                messages.addAll(fieldInfo.copyAnnotationsIntoFieldAnalysisProperties(primitives, e2ImmuAnnotationExpressions)));
        return messages;
    }
}
