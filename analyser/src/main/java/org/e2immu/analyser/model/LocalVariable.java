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

package org.e2immu.analyser.model;

import org.e2immu.analyser.model.variable.Variable;

import java.util.*;

public record LocalVariable(Set<LocalVariableModifier> modifiers,
                            String simpleName,
                            String name,
                            ParameterizedType parameterizedType,
                            List<AnnotationExpression> annotations,
                            TypeInfo owningType,
                            Variable isLocalCopyOf) {

    public LocalVariable {
        Objects.requireNonNull(name);
        Objects.requireNonNull(owningType);
    }

    public LocalVariable translate(TranslationMap translationMap) {
        return new LocalVariable(modifiers, simpleName,
                name, translationMap.translateType(parameterizedType), annotations, owningType, isLocalCopyOf);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalVariable that = (LocalVariable) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "LocalVariable " + name + " of " + parameterizedType;
    }

    public String simpleName() {
        return simpleName;
    }

    public static class Builder {
        private final List<AnnotationExpression> annotations = new ArrayList<>();
        private final Set<LocalVariableModifier> modifiers = new HashSet<>();
        private ParameterizedType parameterizedType;
        private String name;
        private String simpleName;
        private TypeInfo owningType;
        private Variable isLocalCopyOf;

        public Builder setOwningType(TypeInfo owningType) {
            this.owningType = owningType;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }
        public Builder setSimpleName(String name) {
            this.simpleName = name;
            return this;
        }

        public Builder setParameterizedType(ParameterizedType parameterizedType) {
            this.parameterizedType = parameterizedType;
            return this;
        }

        public Builder addModifier(LocalVariableModifier modifier) {
            this.modifiers.add(modifier);
            return this;
        }

        public Builder addAnnotation(AnnotationExpression annotationExpression) {
            this.annotations.add(annotationExpression);
            return this;
        }

        public Builder setIsLocalCopyOf(Variable isLocalCopyOf) {
            this.isLocalCopyOf = isLocalCopyOf;
            return this;
        }

        public LocalVariable build() {
            return new LocalVariable(modifiers, simpleName,
                    name, parameterizedType, annotations, owningType, isLocalCopyOf);
        }
    }
}
