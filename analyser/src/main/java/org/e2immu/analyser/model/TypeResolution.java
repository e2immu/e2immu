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

import org.e2immu.analyser.resolver.SortedType;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/*
The resolver is used recursively at the level of sub-types defined in statements, not at the level
of "normal" subtypes -- only the former require a new EvaluationContext closure.

This recursion results in a SortedType object which will be used to create a PrimaryTypeAnalyser
in the statement analyser.
SortedType is only not-null for sub-types defined in statements; it is kept null for primary types.
 */
public record TypeResolution(SortedType sortedType,
                             Set<TypeInfo> circularDependencies,
                             Set<TypeInfo> superTypesExcludingJavaLangObject,
                             TypeInfo generatedImplementation) {

    public static class Builder {
        private SortedType sortedType;
        private Set<TypeInfo> circularDependencies;
        private Set<TypeInfo> superTypesExcludingJavaLangObject;
        private int countImplementations;
        private int countGeneratedImplementations;
        private TypeInfo implementingType; // valid value only when counts == 1

        public void incrementImplementations(TypeInfo typeInfo, boolean generated) {
            countImplementations++;
            if (generated) countGeneratedImplementations++;
            this.implementingType = typeInfo;
        }

        public Builder setSortedType(SortedType sortedType) {
            this.sortedType = sortedType;
            return this;
        }

        public Builder setCircularDependencies(Set<TypeInfo> circularDependencies) {
            this.circularDependencies = circularDependencies;
            return this;
        }

        public void addCircularDependencies(Set<TypeInfo> typesInCycle) {
            if (circularDependencies == null) circularDependencies = new HashSet<>();
            circularDependencies.addAll(typesInCycle);
        }

        public Builder setSuperTypesExcludingJavaLangObject(Set<TypeInfo> superTypesExcludingJavaLangObject) {
            this.superTypesExcludingJavaLangObject = superTypesExcludingJavaLangObject;
            return this;
        }

        public TypeResolution build() {
            return new TypeResolution(sortedType, // can be null
                    circularDependencies == null ? Set.of() : Set.copyOf(circularDependencies),
                    Set.copyOf(superTypesExcludingJavaLangObject),
                    hasOneKnownGeneratedImplementation() ? Objects.requireNonNull(implementingType) : null);
        }

        public SortedType getSortedType() {
            return sortedType;
        }

        public boolean hasOneKnownGeneratedImplementation() {
            return countImplementations == 1 && countGeneratedImplementations == 1;
        }

        public Set<TypeInfo> getCircularDependencies() {
            return circularDependencies;
        }
    }

    public boolean hasOneKnownGeneratedImplementation() {
        return generatedImplementation != null;
    }
}
