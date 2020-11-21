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


import org.e2immu.analyser.util.AddOnceSet;

import java.util.List;

public abstract class AbstractInspectionBuilder<B> implements Inspection {

    protected final AddOnceSet<AnnotationExpression> annotations = new AddOnceSet<>();

    public B addAnnotation(AnnotationExpression annotationExpression) {
        annotations.add(annotationExpression);
        return (B) this; // unchecked cast saves us 4 copies
    }

    @Override
    public List<AnnotationExpression> getAnnotations() {
        return List.copyOf(annotations.toImmutableSet());
    }

    @Override
    public boolean hasAnnotation(AnnotationExpression annotationExpression) {
        return annotations.contains(annotationExpression);
    }
}
