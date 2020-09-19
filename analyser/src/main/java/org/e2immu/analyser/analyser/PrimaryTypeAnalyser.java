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
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public class PrimaryTypeAnalyser {

    private final PatternMatcher patternMatcher;
    public final TypeInfo primaryType;
    public final List<Analyser> analysers;

    public PrimaryTypeAnalyser(SortedType sortedType, Configuration configuration, @NotNull E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        // TODO move to some other place
        Pattern pattern1 = ConditionalAssignment.pattern1();
        Pattern pattern2 = ConditionalAssignment.pattern2();
        Pattern pattern3 = ConditionalAssignment.pattern3();
        patternMatcher = new PatternMatcher(Map.of(pattern1, ConditionalAssignment.replacement1ToPattern1(pattern1),
                pattern2, ConditionalAssignment.replacement1ToPattern2(pattern2),
                pattern3, ConditionalAssignment.replacement1ToPattern3(pattern3)));


        this.primaryType = Objects.requireNonNull(sortedType.primaryType);
        assert this.primaryType.isPrimaryType();

        ImmutableMap.Builder<ParameterInfo, ParameterAnalyser> parameterAnalysers = new ImmutableMap.Builder<>();
        ImmutableMap.Builder<FieldInfo, FieldAnalyser> fieldAnalysers = new ImmutableMap.Builder<>();

        analysers = sortedType.methodsFieldsSubTypes.stream().flatMap(mfs -> {
            Analyser analyser;
            if (mfs instanceof FieldInfo) {
                FieldInfo fieldInfo = (FieldInfo) mfs;
                MethodAnalyser samAnalyser = null;
                if (fieldInfo.fieldInspection.get().initialiser.isSet()) {
                    FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().initialiser.get();
                    MethodInfo sam = fieldInitialiser.implementationOfSingleAbstractMethod;
                    if (sam != null) {
                        samAnalyser = new MethodAnalyser(sam, true, configuration, patternMatcher, e2ImmuAnnotationExpressions);
                    }
                }
                analyser = new FieldAnalyser(fieldInfo, primaryType, samAnalyser, configuration, patternMatcher, e2ImmuAnnotationExpressions);
                fieldAnalysers.put(fieldInfo, (FieldAnalyser) analyser);
                if (samAnalyser != null) {
                    return List.of(analyser, samAnalyser).stream();
                }
            } else if (mfs instanceof MethodInfo) {
                analyser = new MethodAnalyser((MethodInfo) mfs, false, configuration, patternMatcher, e2ImmuAnnotationExpressions);
                for (ParameterAnalyser parameterAnalyser : ((MethodAnalyser) analyser).parameterAnalysers) {
                    parameterAnalysers.put(parameterAnalyser.parameterInfo, parameterAnalyser);
                }
            } else if (mfs instanceof TypeInfo) {
                analyser = new TypeAnalyser((TypeInfo) mfs, primaryType, configuration, patternMatcher, e2ImmuAnnotationExpressions);
            } else throw new UnsupportedOperationException();
            return List.of(analyser).stream();
        }).collect(Collectors.toList());

        analysers.forEach(analyser -> analyser.initialize(analysers, parameterAnalysers.build(), fieldAnalysers.build()));
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
}
