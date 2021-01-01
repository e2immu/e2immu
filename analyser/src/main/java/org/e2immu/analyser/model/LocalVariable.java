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

import org.e2immu.analyser.model.variable.Variable;

import java.util.*;

public record LocalVariable(Set<LocalVariableModifier> modifiers,
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
        return new LocalVariable(modifiers, name, translationMap.translateType(parameterizedType), annotations, owningType, isLocalCopyOf);
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

    public static class Builder {
        private final List<AnnotationExpression> annotations = new ArrayList<>();
        private final Set<LocalVariableModifier> modifiers = new HashSet<>();
        private ParameterizedType parameterizedType;
        private String name;
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
            return new LocalVariable(modifiers, name, parameterizedType, annotations, owningType, isLocalCopyOf);
        }
    }
}
