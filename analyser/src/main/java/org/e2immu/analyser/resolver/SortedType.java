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

package org.e2immu.analyser.resolver;

import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;

import java.util.List;
import java.util.Objects;

/**
 * Main usage: defined types, where the order of methods, fields and sub-types is important for the analysis phase.
 * <p>
 * This type is also used for sub-resolvers. In that case, the TypeInfo object is not a primary type, but either
 * a locally declared type, an anonymous type, a lambda, ...
 */
public record SortedType(TypeInfo primaryType,
                         List<WithInspectionAndAnalysis> methodsFieldsSubTypes) {
    public SortedType(TypeInfo primaryType, List<WithInspectionAndAnalysis> methodsFieldsSubTypes) {
        this.primaryType = Objects.requireNonNull(primaryType);
        this.methodsFieldsSubTypes = Objects.requireNonNull(methodsFieldsSubTypes);
    }

    @Override
    public String toString() {
        return primaryType.fullyQualifiedName + ": " + methodsFieldsSubTypes;
    }
}
