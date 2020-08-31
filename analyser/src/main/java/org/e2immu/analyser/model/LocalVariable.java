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

import org.e2immu.analyser.model.expression.LocalVariableModifier;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class LocalVariable {
    public final List<AnnotationExpression> annotations;
    public final List<LocalVariableModifier> modifiers;
    public final ParameterizedType parameterizedType;
    @NotNull
    public final String name;

    public LocalVariable(List<LocalVariableModifier> modifiers, @NotNull String name, ParameterizedType parameterizedType, List<AnnotationExpression> annotations) {
        this.parameterizedType = parameterizedType;
        this.name = Objects.requireNonNull(name);
        this.modifiers = modifiers;
        this.annotations = annotations;
    }

    public LocalVariable translate(TranslationMap translationMap) {
        return new LocalVariable(modifiers, name, translationMap.translateType(parameterizedType), annotations);
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

    public Set<String> imports() {
        if (parameterizedType.typeInfo != null) return Set.of(parameterizedType.typeInfo.fullyQualifiedName);
        return Set.of();
    }

    @Override
    public String toString() {
        return "LocalVariable " + name + " of " + parameterizedType;
    }

    public static class LocalVariableBuilder {
        private final List<AnnotationExpression> annotations = new ArrayList<>();
        private final List<LocalVariableModifier> modifiers = new ArrayList<>();
        private ParameterizedType parameterizedType;
        private String name;

        public LocalVariableBuilder setName(String name) {
            this.name = name;
            return this;
        }

        public LocalVariableBuilder setParameterizedType(ParameterizedType parameterizedType) {
            this.parameterizedType = parameterizedType;
            return this;
        }

        public LocalVariableBuilder addModifier(LocalVariableModifier modifier) {
            this.modifiers.add(modifier);
            return this;
        }

        public LocalVariableBuilder addAnnotation(AnnotationExpression annotationExpression) {
            this.annotations.add(annotationExpression);
            return this;
        }

        public LocalVariable build() {
            return new LocalVariable(modifiers, name, parameterizedType, annotations);
        }
    }
}
