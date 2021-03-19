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

package org.e2immu.analyser.annotationxml.model;

import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.annotation.E2Immutable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class HasAnnotations {
    private List<Annotation> annotations = new ArrayList<>();

    void freeze() {
        annotations = List.copyOf(annotations);
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    protected void addAnnotations(List<AnnotationExpression> inspected, List<AnnotationExpression> analysed) {
        Set<String> e2immuAnnotationsWritten = new HashSet<>();
        for (AnnotationExpression ae : inspected) {
            boolean accept = ae.typeInfo().fullyQualifiedName.startsWith(E2Immutable.class.getPackageName())
                    && !ae.e2ImmuAnnotationParameters().isVerifyAbsent();
            if (accept) {
                e2immuAnnotationsWritten.add(ae.typeInfo().fullyQualifiedName);
                Annotation annotation = Annotation.from(ae);
                annotations.add(annotation);
            }
        }
        // these are always our annotations, typically of type COMPUTED
        // but the reader will make them CONTRACT...
        for (AnnotationExpression ae : analysed) {
            if (!e2immuAnnotationsWritten.contains(ae.typeInfo().fullyQualifiedName)) {
                Annotation annotation = Annotation.from(ae);
                annotations.add(annotation);
            }
        }
    }
}
