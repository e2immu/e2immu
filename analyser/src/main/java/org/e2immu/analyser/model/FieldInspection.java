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
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

@E2Immutable(after = "??")
@Container
public class FieldInspection extends Inspection {

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
    public final FirstThen<com.github.javaparser.ast.expr.Expression, FieldInitialiser> initialiser;
    @NotNull
    public final FieldModifier access;

    @NotNull
    public final List<AnnotationExpression> annotations;

    private FieldInspection(@NotNull List<FieldModifier> modifiers,
                            @NotNull FirstThen<com.github.javaparser.ast.expr.Expression, FieldInitialiser> initialiser,
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

    public FieldInspection copy(List<AnnotationExpression> alternativeAnnotations) {
        return new FieldInspection(modifiers, initialiser, ImmutableList.copyOf(alternativeAnnotations));
    }

    public boolean haveInitialiser() {
        return initialiser.isSet() ? initialiser.get().initialiser != EmptyExpression.EMPTY_EXPRESSION : initialiser.getFirst() != EMPTY;
    }

    public static class FieldInspectionBuilder implements BuilderWithAnnotations<FieldInspectionBuilder> {
        private final ImmutableList.Builder<FieldModifier> modifiers = new ImmutableList.Builder<>();
        private final ImmutableList.Builder<AnnotationExpression> annotations = new ImmutableList.Builder<>();
        private com.github.javaparser.ast.expr.Expression initializer;
        private Expression alreadyKnown;

        public FieldInspectionBuilder setInitializer(com.github.javaparser.ast.expr.Expression initializer) {
            this.initializer = initializer;
            return this;
        }

        public FieldInspectionBuilder setInitializer(Expression alreadyKnown) {
            this.alreadyKnown = alreadyKnown;
            return this;
        }

        @Override
        public FieldInspectionBuilder addAnnotation(AnnotationExpression annotation) {
            this.annotations.add(annotation);
            return this;
        }

        public FieldInspectionBuilder addAnnotations(List<AnnotationExpression> annotations) {
            this.annotations.addAll(annotations);
            return this;
        }

        public FieldInspectionBuilder addModifier(FieldModifier modifier) {
            this.modifiers.add(modifier);
            return this;
        }

        public FieldInspectionBuilder addModifiers(List<FieldModifier> modifiers) {
            this.modifiers.addAll(modifiers);
            return this;
        }

        @NotNull
        public FieldInspection build() {
            FirstThen<com.github.javaparser.ast.expr.Expression, FieldInitialiser> firstThen = new FirstThen<>(initializer != null ? initializer : EMPTY);
            if (alreadyKnown != null) firstThen.set(new FieldInitialiser(alreadyKnown, null));
            return new FieldInspection(modifiers.build(), firstThen, annotations.build());
        }
    }

    public static class FieldInitialiser {
        public final Expression initialiser;
        public final MethodInfo implementationOfSingleAbstractMethod;

        public FieldInitialiser(Expression initialiser, MethodInfo implementationOfSingleAbstractMethod) {
            this.implementationOfSingleAbstractMethod = implementationOfSingleAbstractMethod;
            this.initialiser = initialiser;
        }
    }
}
