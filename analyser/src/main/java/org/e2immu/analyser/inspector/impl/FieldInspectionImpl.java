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

package org.e2immu.analyser.inspector.impl;

import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;
import org.e2immu.analyser.inspector.AbstractInspectionBuilder;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.InspectionProvider;
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

    private final Set<FieldModifier> modifiers;
    private final FieldInspection.FieldInitialiser fieldInitialiser;

    private FieldInspectionImpl(@NotNull Set<FieldModifier> modifiers,
                                @NotNull FieldInspection.FieldInitialiser fieldInitialiser,
                                @NotNull List<AnnotationExpression> annotations,
                                @NotNull Access access,
                                boolean synthetic) {
        super(annotations, access, synthetic);
        Objects.requireNonNull(modifiers);
        this.fieldInitialiser = fieldInitialiser;
        this.modifiers = modifiers;
    }

    @Override
    public Set<FieldModifier> getModifiers() {
        return modifiers;
    }

    @Override
    public FieldInitialiser getFieldInitialiser() {
        return fieldInitialiser;
    }

    public static class Builder extends AbstractInspectionBuilder<FieldInspection.Builder>
            implements FieldInspection, FieldInspection.Builder {
        private final Set<FieldModifier> modifiers = new HashSet<>();
        private final FieldInfo fieldInfo;

        private com.github.javaparser.ast.expr.Expression initialiserExpression;
        private Expression inspectedInitialiserExpression;
        private FieldInspection.FieldInitialiser fieldInitialiser;

        public Builder(FieldInfo fieldInfo) {
            this.fieldInfo = fieldInfo;
        }

        public com.github.javaparser.ast.expr.Expression getInitialiserExpression() {
            return initialiserExpression;
        }

        public void setInitialiserExpression(com.github.javaparser.ast.expr.Expression initialiserExpression) {
            this.initialiserExpression = initialiserExpression;
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

        @NotNull
        public FieldInspectionImpl build(InspectionProvider inspectionProvider) {
            Identifier id = initialiserExpression == null ? Identifier.generate("field initializer")
                    : Identifier.from(initialiserExpression);
            FieldInitialiser fi = fieldInitialiser != null ? fieldInitialiser :
                    inspectedInitialiserExpression == null
                            ? null : new FieldInitialiser(inspectedInitialiserExpression, id);
            if (accessNotYetComputed()) computeAccess(inspectionProvider);
            return new FieldInspectionImpl(getModifiers(), fi, getAnnotations(), getAccess(), isSynthetic());
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
        public void computeAccess(InspectionProvider inspectionProvider) {
            TypeInspection typeInspection = inspectionProvider.getTypeInspection(fieldInfo.owner);
            Access fromType = typeInspection.getAccess();
            Access fromModifier = accessFromFieldModifier();
            Access combined = fromModifier.combine(fromType);
            setAccess(combined);
        }

        private Access accessFromFieldModifier() {
            if (modifiers.contains(FieldModifier.PUBLIC)) return Access.PUBLIC;
            if (modifiers.contains(FieldModifier.PRIVATE)) return Access.PRIVATE;
            if (modifiers.contains(FieldModifier.PROTECTED)) return Access.PROTECTED;
            return Access.PACKAGE;
        }
    }
}
