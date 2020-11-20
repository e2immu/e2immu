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

import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.google.common.collect.ImmutableList;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

public class FieldInspectionImpl extends InspectionImpl {

    public static final com.github.javaparser.ast.expr.Expression EMPTY = new com.github.javaparser.ast.expr.Expression() {
        @Override
        public <R, A> R accept(GenericVisitor<R, A> v, A arg) {
            return null;
        }

        @Override
        public <A> void accept(VoidVisitor<A> v, A arg) {

        }
    };

    @NotNull
    public final List<FieldModifier> modifiers;
    public final FieldInspection.FieldInitialiser initialiser;
    @NotNull
    public final FieldModifier access;

    @NotNull
    public final List<AnnotationExpression> annotations;

    private FieldInspectionImpl(@NotNull List<FieldModifier> modifiers,
                                @NotNull FieldInspection.FieldInitialiser initialiser,
                                @NotNull List<AnnotationExpression> annotations) {
        super(annotations);
        Objects.requireNonNull(modifiers);
        this.annotations = annotations;
        this.initialiser = initialiser;
        this.modifiers = modifiers;
        access = computeAccess();
    }

    private FieldModifier computeAccess() {
        if (modifiers.contains(FieldModifier.PRIVATE)) return FieldModifier.PRIVATE;
        if (modifiers.contains(FieldModifier.PROTECTED)) return FieldModifier.PROTECTED;
        if (modifiers.contains(FieldModifier.PUBLIC)) return FieldModifier.PUBLIC;
        return FieldModifier.PACKAGE;
    }

    public FieldInspectionImpl copy(List<AnnotationExpression> alternativeAnnotations) {
        return new FieldInspectionImpl(modifiers, initialiser, ImmutableList.copyOf(alternativeAnnotations));
    }

    public static class Builder implements BuilderWithAnnotations<Builder> {
        private final ImmutableList.Builder<FieldModifier> modifiers = new ImmutableList.Builder<>();
        private final ImmutableList.Builder<AnnotationExpression> annotations = new ImmutableList.Builder<>();
        private com.github.javaparser.ast.expr.Expression initializer;
        private Expression inspectedInitialiser;
        private FieldInspection.FieldInitialiser fieldInitialiser;

        public com.github.javaparser.ast.expr.Expression getInitializer() {
            return initializer;
        }

        public Builder setInitializer(com.github.javaparser.ast.expr.Expression initializer) {
            this.initializer = initializer;
            return this;
        }

        public Builder setInspectedInitializer(Expression inspectedInitialiser) {
            this.inspectedInitialiser = inspectedInitialiser;
            return this;
        }

        @Override
        public Builder addAnnotation(AnnotationExpression annotation) {
            this.annotations.add(annotation);
            return this;
        }

        public Builder addAnnotations(List<AnnotationExpression> annotations) {
            this.annotations.addAll(annotations);
            return this;
        }

        public Builder addModifier(FieldModifier modifier) {
            this.modifiers.add(modifier);
            return this;
        }

        public Builder addModifiers(List<FieldModifier> modifiers) {
            this.modifiers.addAll(modifiers);
            return this;
        }

        public void setFieldInitializer(FieldInspection.FieldInitialiser fieldInitialiser) {
            this.fieldInitialiser = fieldInitialiser;
        }

        @NotNull
        public FieldInspectionImpl build() {
            return new FieldInspectionImpl(modifiers.build(),
                    fieldInitialiser != null ? fieldInitialiser :
                            new FieldInspection.FieldInitialiser(inspectedInitialiser, null, false),
                    annotations.build());
        }
    }
}
