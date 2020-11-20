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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.annotation.AnnotationMode;
import org.e2immu.annotation.AnnotationType;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public interface Analysis {

    default Stream<Map.Entry<AnnotationExpression, Boolean>> getAnnotationStream() {
        return Stream.empty();
    }

    default Boolean getAnnotation(AnnotationExpression annotationExpression) {
        return null;
    }

    default int getProperty(VariableProperty variableProperty) {
        return Level.DELAY;
    }

    Location location();

    default AnnotationMode annotationMode() {
        return AnnotationMode.DEFENSIVE;
    }

    default void peekIntoAnnotations(AnnotationExpression annotation, Set<TypeInfo> annotationsSeen, StringBuilder sb) {
        AnnotationType annotationType = e2immuAnnotation(annotation);
        if (annotationType != null && annotationType != AnnotationType.CONTRACT) {
            // so we have one of our own annotations, and we know its type
            Boolean verified = getAnnotation(annotation);
            if (verified != null) {
                boolean ok = verified && annotationType == AnnotationType.VERIFY
                        || !verified && annotationType == AnnotationType.VERIFY_ABSENT;
                annotationsSeen.add(annotation.typeInfo());
                if (ok) {
                    sb.append("/*OK*/");
                } else {
                    sb.append("/*FAIL*/");
                }
            } else {
                if (annotationType == AnnotationType.VERIFY) {
                    sb.append("/*FAIL:DELAYED*/");
                } else if (annotationType == AnnotationType.VERIFY_ABSENT) {
                    sb.append("/*OK:DELAYED*/");
                }
            }
        }
        if (annotationType == AnnotationType.CONTRACT) annotationsSeen.add(annotation.typeInfo());
    }

    static AnnotationType e2immuAnnotation(AnnotationExpression annotation) {
        if (annotation.typeInfo().fullyQualifiedName.startsWith("org.e2immu.annotation")) {
            return annotation.extract("type", AnnotationType.VERIFY);
        }
        return null;
    }

    default Analysis build() {
        throw new UnsupportedOperationException();
    }

    default Map<VariableProperty, Integer> getProperties(Set<VariableProperty> forwardPropertiesOnParameters) {
        return Map.of();
    }

    default int getPropertyAsIs(VariableProperty variableProperty) {
        return getProperty(variableProperty);
    }

    default int internalGetProperty(VariableProperty variableProperty) {
        return Level.DELAY;
    }

    /**
     * Helps to decide whether absence of a property must equal a delay. If the analyser is actively going over the method's code,
     * then this method has to return true.
     * <p>
     * Note that in the annotated APIs, a method has a method analysis builder even if it is not being analysed, but its companion methods
     * are.
     *
     * @return true when the method is being analysed, irrespective of whether its companion methods are being analysed.
     */
    default boolean isBeingAnalysed() {
        return false;
    }
}
