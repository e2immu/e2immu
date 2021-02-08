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

import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.Either;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
