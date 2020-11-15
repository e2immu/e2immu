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

import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Optional;

public interface WithInspectionAndAnalysis {

    Inspection getInspection();

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
            return getInspection().annotations.stream()
                    .anyMatch(ae -> ae.typeInfo.fullyQualifiedName.equals(annotation.typeInfo.fullyQualifiedName));
        }
        return analysis.getAnnotation(annotation);
    }

    default Optional<Boolean> error(AbstractAnalysisBuilder analysisBuilder, Class<?> annotation, AnnotationExpression expression) {
        Optional<Boolean> mustBeAbsent = hasInspectedAnnotation(annotation).map(AnnotationExpression::isVerifyAbsent);
        if (mustBeAbsent.isEmpty()) return Optional.empty(); // no error, no check!
        Boolean actual = analysisBuilder.annotations.getOtherwiseNull(expression);
        if (actual == null && !mustBeAbsent.get() || mustBeAbsent.get() == actual) {
            return mustBeAbsent; // error!!!
        }
        return Optional.empty(); // no error
    }

    default Optional<Boolean> error(AbstractAnalysisBuilder analysisBuilder, Class<?> annotation, List<AnnotationExpression> expressions) {
        Optional<Boolean> mustBeAbsent = hasInspectedAnnotation(annotation).map(AnnotationExpression::isVerifyAbsent);
        if (mustBeAbsent.isEmpty()) return Optional.empty(); // no error, no check!
        for (AnnotationExpression expression : expressions) {
            Boolean actual = analysisBuilder.annotations.getOtherwiseNull(expression);
            if (actual != null) {
                return mustBeAbsent.get() == actual ? mustBeAbsent : Optional.empty();
            }
        }
        return mustBeAbsent.get() ? Optional.empty() : mustBeAbsent;
    }

    String fullyQualifiedName();

    void setAnalysis(Analysis analysis);
}
