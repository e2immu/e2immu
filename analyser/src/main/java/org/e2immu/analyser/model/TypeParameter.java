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

import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Either;
import org.e2immu.analyser.util.SetOnce;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECT;
import static org.e2immu.analyser.util.Logger.log;

public class TypeParameter implements NamedType {

    // the type the parameter belongs to
    public final Either<TypeInfo, MethodInfo> owner;
    public final String name;
    public final int index;

    public final SetOnce<TypeParameterInspection> typeParameterInspection = new SetOnce<>();

    public TypeParameter(TypeInfo typeInfo, String name, int index) {
        this.name = name;
        this.index = index;
        this.owner = Either.left(typeInfo);
    }

    public TypeParameter(MethodInfo typeInfo, String name, int index) {
        this.name = name;
        this.index = index;
        this.owner = Either.right(typeInfo);
    }

    public String stream() {
        if (typeParameterInspection.isSet() && !typeParameterInspection.get().typeBounds.isEmpty()) {
            return name + " extends " + typeParameterInspection.get()
                    .typeBounds.stream().map(ParameterizedType::stream).collect(Collectors.joining(" & "));
        }
        return name;
    }

    @Override
    public String toString() {
        String where = owner.isLeft() ? owner.getLeft().fullyQualifiedName :
                owner.getRight().fullyQualifiedName();
        return name + " as #" + index + " in " + where;
    }

    @Override
    public String simpleName() {
        return name;
    }

    public boolean isMethodTypeParameter() {
        return owner.isRight();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeParameter that = (TypeParameter) o;
        return owner.equals(that.owner) &&
                name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name);
    }

    public void inspect(TypeContext typeContext, com.github.javaparser.ast.type.TypeParameter typeParameter) {
        List<ParameterizedType> typeBounds = new ArrayList<>();
        typeParameter.getTypeBound().forEach(cit -> {
            log(INSPECT, "Inspecting type parameter {}", cit.getName().asString());
            ParameterizedType bound = ParameterizedType.from(typeContext, cit);
            typeBounds.add(bound);
        });
        typeParameterInspection.set(new TypeParameterInspection(typeBounds));
    }
}
