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

import org.e2immu.analyser.parser.SideEffectContext;

import java.util.Objects;

/**
 * Variable representing the "this" keyword
 */
public class This implements Variable {
    public final TypeInfo typeInfo;

    public This(TypeInfo typeInfo) {
        this.typeInfo = typeInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        This aThis = (This) o;
        return typeInfo.equals(aThis.typeInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeInfo);
    }

    @Override
    public ParameterizedType parameterizedType() {
        return typeInfo.asParameterizedType();
    }

    @Override
    public ParameterizedType concreteReturnType() {
        return parameterizedType();
    }

    @Override
    public String name() {
        return "this"; // TODO could also be typeInfo.name()+".this"
    }

    @Override
    public String detailedString() {
        return "this keyword (of " + typeInfo.fullyQualifiedName + ")";
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return SideEffect.LOCAL;
    }
}
