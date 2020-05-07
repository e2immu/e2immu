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

import org.e2immu.analyser.parser.TypeContext;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public interface WithInspectionAndAnalysis {
    static Integer boolToInt(Boolean b) {
        return b == null ? null : b ? 1 : 0;
    }

    Analysis getAnalysis();

    Inspection getInspection();

    String name();

    Optional<AnnotationExpression> hasTestAnnotation(Class<?> annotation);

    default boolean hasBeenDefined() {
        return true;
    }

    default Boolean annotatedWith(AnnotationExpression annotation) {
        if (hasBeenDefined()) {
            return getAnalysis().annotations.getOtherwiseNull(annotation);
        }
        return getInspection().annotations.stream()
                .anyMatch(ae -> ae.typeInfo.fullyQualifiedName.equals(annotation.typeInfo.fullyQualifiedName));
    }

    default Integer numericAnnotatedWith(AnnotationExpression annotation) {
        if (hasBeenDefined()) {
            // TODO: add number @NotNull(n=3), @Immutable(n=...)
            return boolToInt(getAnalysis().annotations.getOtherwiseNull(annotation));
        }
        return boolToInt(getInspection().annotations.stream()
                .anyMatch(ae -> ae.typeInfo.fullyQualifiedName.equals(annotation.typeInfo.fullyQualifiedName)));
    }

    default Integer getContainer(TypeContext typeContext) {
        Boolean container = annotatedWith(typeContext.container.get());
        if (container == null) return null; // delay
        if (container) return 1;
        Boolean e1container = annotatedWith(typeContext.e1Container.get());
        if (e1container != null) return 1;
        Boolean e2container = annotatedWith(typeContext.e2Container.get());
        if (e2container != null) return 1;
        return 0; // not a container
    }

    default Integer getImmutable(TypeContext typeContext) {
        Boolean e1Immutable = annotatedWith(typeContext.e1Immutable.get());
        if (e1Immutable == null) return null;
        Boolean e2Immutable = annotatedWith(typeContext.e2Immutable.get());
        if (e2Immutable == null) return null;
        Boolean e2Container = annotatedWith(typeContext.e2Container.get());
        if (e2Container == null) return null;
        Boolean e1Container = annotatedWith(typeContext.e1Container.get());
        if (e1Container == null) return null;
        if (e2Immutable || e2Container) return 2;
        if (e1Immutable || e1Container) return 1;
        return 0;
    }

    default Optional<Boolean> error(Class<?> annotation, AnnotationExpression expression) {
        Optional<Boolean> mustBeAbsent = hasTestAnnotation(annotation).map(AnnotationExpression::isVerifyAbsent);
        if (mustBeAbsent.isEmpty()) return Optional.empty(); // no error, no check!
        Boolean actual = getAnalysis().annotations.getOtherwiseNull(expression);
        if (actual == null && !mustBeAbsent.get() || mustBeAbsent.get() == actual) {
            return mustBeAbsent; // error!!!
        }
        return Optional.empty(); // no error
    }
}
