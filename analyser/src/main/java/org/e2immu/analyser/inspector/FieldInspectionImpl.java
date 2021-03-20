/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.inspector;

import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldInspection;
import org.e2immu.analyser.model.FieldModifier;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    private final boolean synthetic;
    private final Set<FieldModifier> modifiers;
    private final FieldInspection.FieldInitialiser fieldInitialiser;
    private final FieldModifier access;

    private FieldInspectionImpl(@NotNull Set<FieldModifier> modifiers,
                                @NotNull FieldInspection.FieldInitialiser fieldInitialiser,
                                @NotNull List<AnnotationExpression> annotations,
                                @NotNull FieldModifier access,
                                boolean synthetic) {
        super(annotations);
        Objects.requireNonNull(modifiers);
        this.fieldInitialiser = fieldInitialiser;
        this.modifiers = modifiers;
        this.access = Objects.requireNonNull(access);
        this.synthetic = synthetic;
    }

    @Override
    public boolean isSynthetic() {
        return synthetic;
    }

    @Override
    public Set<FieldModifier> getModifiers() {
        return modifiers;
    }

    @Override
    public FieldInitialiser getFieldInitialiser() {
        return fieldInitialiser;
    }

    @Override
    public FieldModifier getAccess() {
        return access;
    }

    public static class Builder extends AbstractInspectionBuilder<Builder> implements FieldInspection {
        private final Set<FieldModifier> modifiers = new HashSet<>();
        private com.github.javaparser.ast.expr.Expression initialiserExpression;
        private Expression inspectedInitialiserExpression;
        private FieldInspection.FieldInitialiser fieldInitialiser;
        private boolean isSynthetic;

        public com.github.javaparser.ast.expr.Expression getInitialiserExpression() {
            return initialiserExpression;
        }

        public Builder setInitialiserExpression(com.github.javaparser.ast.expr.Expression initialiserExpression) {
            this.initialiserExpression = initialiserExpression;
            return this;
        }

        public Builder setInspectedInitialiserExpression(Expression inspectedInitialiser) {
            this.inspectedInitialiserExpression = inspectedInitialiser;
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

        public void setSynthetic(boolean synthetic) {
            isSynthetic = synthetic;
        }

        @NotNull
        public FieldInspectionImpl build() {
            return new FieldInspectionImpl(getModifiers(),
                    fieldInitialiser != null ? fieldInitialiser :
                            inspectedInitialiserExpression == null ? null : new FieldInspection.FieldInitialiser
                                    (inspectedInitialiserExpression, null, false),
                    getAnnotations(), getAccess(), isSynthetic);
        }

        @Override
        public Set<FieldModifier> getModifiers() {
            return Set.copyOf(modifiers);
        }

        @Override
        public FieldInitialiser getFieldInitialiser() {
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
