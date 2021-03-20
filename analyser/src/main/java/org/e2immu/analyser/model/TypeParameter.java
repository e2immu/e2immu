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
import org.e2immu.support.Either;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface TypeParameter extends NamedType {

    // the type the parameter belongs to
    Either<TypeInfo, MethodInfo> getOwner();

    String getName();

    int getIndex();

    List<ParameterizedType> getTypeBounds();

    default OutputBuilder output(Qualification qualification) {
        return output(InspectionProvider.DEFAULT, qualification, new HashSet<>());
    }

    OutputBuilder output(InspectionProvider inspectionProvider, Qualification qualification, Set<TypeParameter> visitedTypeParameters);

    @Override
    default String simpleName() {
        return getName();
    }

    boolean isMethodTypeParameter();

}
