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

import org.e2immu.analyser.model.variable.VariableNature;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/*
The VariableNature of a LocalVariable is copied into VariableInfoContainer; both have often the same object.
In some situations, the VIC has a different VariableNature, which always takes precedence.

This always the case when the variable was created by the inspection (rather than being synthetic, created by
the analyser. Then, they have identical values.)
*/
public record LocalVariable(Set<LocalVariableModifier> modifiers,
                            String name,
                            ParameterizedType parameterizedType,
                            List<AnnotationExpression> annotations,
                            TypeInfo owningType,
                            VariableNature nature) {

    // testing!
    public LocalVariable(String name, ParameterizedType parameterizedType) {
        this(Set.of(), name, parameterizedType, List.of(), parameterizedType.typeInfo, VariableNature.METHOD_WIDE);
    }

    public LocalVariable {
        Objects.requireNonNull(name);
        Objects.requireNonNull(owningType);
        Objects.requireNonNull(nature);
    }

    public LocalVariable translate(TranslationMap translationMap) {
        ParameterizedType translatedType = translationMap.translateType(parameterizedType);
        if(translatedType == parameterizedType) return this;
        return new LocalVariable(modifiers, name, translatedType, annotations, owningType, nature);
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
        private List<AnnotationExpression> annotations;
        private Set<LocalVariableModifier> modifiers;
        private ParameterizedType parameterizedType;
        private String name;
        private TypeInfo owningType;
        private VariableNature nature = VariableNature.METHOD_WIDE;

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

        public void setModifiers(Set<LocalVariableModifier> modifiers) {
            this.modifiers = modifiers;
        }

        public Builder setNature(VariableNature nature) {
            this.nature = nature;
            return this;
        }

        public LocalVariable build() {
            return new LocalVariable(modifiers == null ? Set.of() : Set.copyOf(modifiers),
                    name, parameterizedType, annotations == null ? List.of() : List.copyOf(annotations), owningType,
                    nature);
        }

        public void setAnnotations(List<AnnotationExpression> annotations) {
            this.annotations = annotations;
        }
    }
}
