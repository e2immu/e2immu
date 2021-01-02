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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.AbstractAnalysisBuilder;
import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.output.TypeName;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.*;
import java.util.stream.Stream;

public interface WithInspectionAndAnalysis {

    Inspection getInspection();

    boolean hasBeenInspected();

    String name();

    Optional<AnnotationExpression> hasInspectedAnnotation(Class<?> annotation);

    UpgradableBooleanMap<TypeInfo> typesReferenced();

    TypeInfo primaryType();

    // byte code inspection + annotated APIs: hasBeenDefined on type == false
    // classes and enumerations with at least one field with initialiser or method with code block: hasBeenDefined == true
    // annotation classes: hasBeenDefined == false
    // interfaces: only for methods with code block, and initialisers, if the type has been defined

    default Boolean annotatedWith(Analysis analysis, AnnotationExpression annotation) {
        if (primaryType().shallowAnalysis()) {
            return getInspection().getAnnotations().stream()
                    .anyMatch(ae -> ae.typeInfo().fullyQualifiedName.equals(annotation.typeInfo().fullyQualifiedName));
        }
        return analysis.getAnnotation(annotation).isPresent();
    }

    default Optional<Boolean> error(AbstractAnalysisBuilder analysisBuilder, Class<?> annotation, AnnotationExpression expression) {
        Optional<Boolean> mustBeAbsent = hasInspectedAnnotation(annotation).map(AnnotationExpression::e2ImmuAnnotationParameters).map(AnnotationParameters::isVerifyAbsent);
        Boolean actual = analysisBuilder.annotations.getOtherwiseNull(expression);
        if (mustBeAbsent.isEmpty()) {
            analysisBuilder.annotationChecks.put(expression, actual == Boolean.TRUE ? Analysis.AnnotationCheck.COMPUTED :
                    Analysis.AnnotationCheck.OK_ABSENT);
            return Optional.empty(); // no error, because no check!
        }
        if (!mustBeAbsent.get()) {
            // we expect the annotation to be present
            if (actual == null || !actual) {
                analysisBuilder.annotationChecks.put(expression, Analysis.AnnotationCheck.MISSING);
                return mustBeAbsent; // error!!!
            }
            analysisBuilder.annotationChecks.put(expression, Analysis.AnnotationCheck.OK);
            return Optional.empty(); // no error
        }
        // we expect the annotation to be absent
        if (actual == Boolean.TRUE) {
            analysisBuilder.annotationChecks.put(expression, Analysis.AnnotationCheck.PRESENT);
            return mustBeAbsent; // error
        }
        analysisBuilder.annotationChecks.put(expression, Analysis.AnnotationCheck.OK_ABSENT);
        return Optional.empty(); // no error, annotation is not there
    }

    String fullyQualifiedName();

    void setAnalysis(Analysis analysis);

    Analysis getAnalysis();

    boolean hasBeenAnalysed();

    default Stream<OutputBuilder> buildAnnotationOutput() {
        Set<AnnotationExpression> annotationDuplicateChecks = new HashSet<>();
        List<AnnotationExpression> annotations = new LinkedList<>();
        List<OutputBuilder> perAnnotation = new ArrayList<>();

        if (hasBeenAnalysed()) {
            // computed annotations get priority; they'll be commented on
            getAnalysis().getAnnotationStream()
                    .filter(e -> e.getValue().hasBeenComputed())
                    .forEach(e -> annotations.add(e.getKey()));
            annotationDuplicateChecks.addAll(annotations);
        }
        if (hasBeenInspected()) {
            // ignoring those that are already present; note that in the inspection, there can be multiple
            // annotations of the same type; they are definitely not ours, not computed.
            for (AnnotationExpression annotation : getInspection().getAnnotations()) {
                if (!annotationDuplicateChecks.contains(annotation)) {
                    annotations.add(annotation);
                }
            }
        }
        for (AnnotationExpression annotation : annotations) {
            Analysis.AnnotationCheck annotationCheck = getAnalysis().getAnnotation(annotation);
            if (annotationCheck != Analysis.AnnotationCheck.ABSENT) {
                OutputBuilder outputBuilder = new OutputBuilder().add(annotation.output());
                if (annotationCheck.writeComment()) {
                    outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT).add(new Text(annotationCheck.toString()))
                            .add(Symbol.RIGHT_BLOCK_COMMENT);
                }
                perAnnotation.add(outputBuilder);
            }
        }
        if (perAnnotation.size() > 1) {
            perAnnotation.sort(Comparator.comparing(a -> ((TypeName) a.get(1)).simpleName()));
        }
        return perAnnotation.stream();
    }
}
