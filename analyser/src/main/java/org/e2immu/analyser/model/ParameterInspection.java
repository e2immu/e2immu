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

import com.google.common.collect.ImmutableList;
import org.e2immu.annotation.*;

import java.util.ArrayList;
import java.util.List;

@NotNull
@NullNotAllowed
public class ParameterInspection extends Inspection {

    public final MethodInfo owner;
    public final boolean varArgs;

    private ParameterInspection(MethodInfo owner, List<AnnotationExpression> annotations, boolean varArgs) {
        super(annotations);
        this.owner = owner;
        this.varArgs = varArgs;
    }

    public ParameterInspection copy(List<AnnotationExpression> alternativeAnnotations) {
        return new ParameterInspection(owner, ImmutableList.copyOf(alternativeAnnotations), varArgs);
    }

    @Container
    public static class ParameterInspectionBuilder implements BuilderWithAnnotations<ParameterInspectionBuilder> {
        private final List<AnnotationExpression> annotations = new ArrayList<>();
        private boolean varArgs;

        @Fluent
        public ParameterInspectionBuilder setVarArgs(boolean varArgs) {
            this.varArgs = varArgs;
            return this;
        }

        @Override
        @Fluent
        public ParameterInspectionBuilder addAnnotation(@NullNotAllowed AnnotationExpression annotationExpression) {
            annotations.add(annotationExpression);
            return this;
        }

        @NotModified
        @NotNull
        public ParameterInspection build(@NullNotAllowed MethodInfo owner) {
            return new ParameterInspection(owner, ImmutableList.copyOf(annotations), varArgs);
        }
    }
}
