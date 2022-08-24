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

import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.Either;

import java.util.List;
import java.util.Set;

public interface TypeParameter extends NamedType {

    // the type the parameter belongs to
    @NotNull
    Either<TypeInfo, MethodInfo> getOwner();

    @NotNull
    String getName();

    int getIndex();

    @NotNull(content = true)
    List<ParameterizedType> getTypeBounds();

    /**
     * @param inspectionProvider    to dig deeper recursively
     * @param qualification         distinguishing name, FQN?
     * @param visitedTypeParameters if not null, we're in the definition, and need to provide the type bounds;
     *                              a set to avoid duplication inside the type bounds
     * @return output object
     */
    @NotNull
    OutputBuilder output(InspectionProvider inspectionProvider,
                         Qualification qualification,
                         Set<TypeParameter> visitedTypeParameters);

    @Override
    default String simpleName() {
        return getName();
    }

    boolean isMethodTypeParameter();

    Boolean isAnnotatedWithIndependent();

    default TypeInfo primaryType() {
        return getOwner().isLeft() ? getOwner().getLeft().primaryType() : getOwner().getRight().primaryType();
    }

    default boolean isUnbound() {
        return getTypeBounds().isEmpty();
    }

    ParameterizedType toParameterizedType();
}
