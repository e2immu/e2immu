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
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Either;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECT;
import static org.e2immu.analyser.util.Logger.log;

public class TypeParameterImpl implements TypeParameter {

    public final Either<TypeInfo, MethodInfo> owner;
    public final String name;
    public final int index;
    public final List<ParameterizedType> typeBounds;

    private TypeParameterImpl(Either<TypeInfo, MethodInfo> owner, String name, int index, List<ParameterizedType> typeBounds) {
        this.name = name;
        this.index = index;
        this.owner = owner;
        this.typeBounds = typeBounds;
    }


    @Override
    public String toString() {
        String where = owner.isLeft() ? owner.getLeft().fullyQualifiedName :
                owner.getRight().fullyQualifiedName();
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
        return owner;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public List<ParameterizedType> getTypeBounds() {
        return typeBounds;
    }

    @Override
    public String getName() {
        return name;
    }

    public static class Builder implements TypeParameter {
        private TypeInfo typeInfo;
        private MethodInfo methodInfo;
        private final List<ParameterizedType> typeBounds = new ArrayList<>();
        private String name;
        private int index;

        public void setTypeInfo(TypeInfo typeInfo) {
            this.typeInfo = typeInfo;
        }

        public Builder setMethodInfo(MethodInfo methodInfo) {
            this.methodInfo = methodInfo;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setIndex(int index) {
            this.index = index;
            return this;
        }

        public TypeParameterImpl build() {
            return new TypeParameterImpl(getOwner(), name, index, ImmutableList.copyOf(typeBounds));
        }

        public void computeTypeBounds(TypeContext typeContext, com.github.javaparser.ast.type.TypeParameter typeParameter) {
            typeParameter.getTypeBound().forEach(cit -> {
                log(INSPECT, "Inspecting type parameter {}", cit.getName().asString());
                ParameterizedType bound = ParameterizedType.from(typeContext, cit);
                typeBounds.add(bound);
            });
        }

        @Override
        public Either<TypeInfo, MethodInfo> getOwner() {
            return typeInfo != null ? Either.left(typeInfo) : Either.right(methodInfo);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public List<ParameterizedType> getTypeBounds() {
            return typeBounds;
        }
    }

}
