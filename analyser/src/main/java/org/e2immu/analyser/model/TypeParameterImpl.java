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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.util.Either;
import org.e2immu.analyser.util.SetOnce;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECT;
import static org.e2immu.analyser.util.Logger.log;

public class TypeParameterImpl implements TypeParameter {

    public final String name;
    public final int index;

    // typeInfo can be set straight away, but methodInfo has to wait until
    // method building is sufficiently far
    private final SetOnce<Either<TypeInfo, MethodInfo>> owner = new SetOnce<>();
    private final SetOnce<List<ParameterizedType>> typeBounds = new SetOnce<>();

    public TypeParameterImpl(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public TypeParameterImpl(TypeInfo typeInfo, String name, int index) {
        this(name, index);
        owner.set(Either.left(typeInfo));
    }

    @Override
    public String toString() {
        String where = owner.isSet() ? (owner.get().isLeft() ? owner.get().getLeft().fullyQualifiedName :
                owner.get().getRight().fullyQualifiedName()) : "<no owner yet>";
        return name + " as #" + index + " in " + where;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeParameterImpl that = (TypeParameterImpl) o;
        return owner.equals(that.owner) &&
                name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name);
    }

    @Override
    public Either<TypeInfo, MethodInfo> getOwner() {
        return owner.getOrElse(null);
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public List<ParameterizedType> getTypeBounds() {
        return typeBounds.getOrElse(List.of());
    }

    @Override
    public String getName() {
        return name;
    }

    public void setMethodInfo(MethodInfo methodInfo) {
        owner.set(Either.right(methodInfo));
    }
    // from method and type inspector

    public void inspect(TypeContext typeContext, com.github.javaparser.ast.type.TypeParameter typeParameter) {
        List<ParameterizedType> typeBounds = new ArrayList<>();
        typeParameter.getTypeBound().forEach(cit -> {
            log(INSPECT, "Inspecting type parameter {}", cit.getName().asString());
            ParameterizedType bound = ParameterizedType.from(typeContext, cit);
            typeBounds.add(bound);
        });
        setTypeBounds(typeBounds);
    }

    // from byte code inspector

    public void setTypeBounds(List<ParameterizedType> typeBounds) {
        this.typeBounds.set(ImmutableList.copyOf(typeBounds));
    }

    @Override
    public boolean isMethodTypeParameter() {
        return !owner.isSet() || owner.get().isRight();
    }
}
