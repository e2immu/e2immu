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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FieldInspectionImpl extends InspectionImpl implements FieldInspection {

    public static final com.github.javaparser.ast.expr.Expression EMPTY = new com.github.javaparser.ast.expr.Expression() {
        @Override
        public <R, A> R accept(GenericVisitor<R, A> v, A arg) {
            return null;
        }

        @Override
        public <A> void accept(VoidVisitor<A> v, A arg) {

        }
    };

    private final List<FieldModifier> modifiers;
    private final FieldInspection.FieldInitialiser initialiser;
    private final FieldModifier access;

    private FieldInspectionImpl(@NotNull List<FieldModifier> modifiers,
                                @NotNull FieldInspection.FieldInitialiser initialiser,
                                @NotNull List<AnnotationExpression> annotations,
                                @NotNull FieldModifier access) {
        super(annotations);
        Objects.requireNonNull(modifiers);
        this.initialiser = initialiser;
        this.modifiers = modifiers;
        this.access = Objects.requireNonNull(access);
    }

    @Override
    public List<FieldModifier> getModifiers() {
        return modifiers;
    }

    @Override
    public FieldInitialiser getInitialiser() {
        return initialiser;
    }

    @Override
    public FieldModifier getAccess() {
        return access;
    }

    public static class Builder extends AbstractInspectionBuilder<Builder> implements FieldInspection {
        private final List<FieldModifier> modifiers = new ArrayList<>();
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

        public Builder addAnnotations(List<AnnotationExpression> annotations) {
            annotations.forEach(this::addAnnotation);
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
            return new FieldInspectionImpl(getModifiers(),
                    fieldInitialiser != null ? fieldInitialiser :
                            inspectedInitialiser == null ? null :
                                    new FieldInspection.FieldInitialiser(inspectedInitialiser, null, false),
                    getAnnotations(), getAccess());
        }

        @Override
        public List<FieldModifier> getModifiers() {
            return ImmutableList.copyOf(modifiers);
        }

        @Override
        public FieldInitialiser getInitialiser() {
            return fieldInitialiser;
        }

        @Override
        public FieldModifier getAccess() {
            if (modifiers.contains(FieldModifier.PRIVATE)) return FieldModifier.PRIVATE;
            if (modifiers.contains(FieldModifier.PROTECTED)) return FieldModifier.PROTECTED;
            if (modifiers.contains(FieldModifier.PUBLIC)) return FieldModifier.PUBLIC;
            return FieldModifier.PACKAGE;
        }
    }
}
