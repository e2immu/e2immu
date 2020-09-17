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

import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.Objects;
import java.util.Set;

/**
 * Variable representing the "this" keyword
 */
public class This implements Variable {
    public final TypeInfo typeInfo;
    public final boolean explicitlyWriteType;
    public final boolean writeSuper;

    public This(TypeInfo typeInfo) {
        this(typeInfo, false, false);
    }

    public This(TypeInfo typeInfo, boolean explicitlyWriteType, boolean writeSuper) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.explicitlyWriteType = explicitlyWriteType;
        this.writeSuper = writeSuper;
    }

    @Override
    public int variableOrder() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        This aThis = (This) o;
        return typeInfo.equals(aThis.typeInfo) && writeSuper == aThis.writeSuper;
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
        if (explicitlyWriteType) return typeInfo.simpleName + (writeSuper ? ".super" : ".this");
        return writeSuper ? "super" : "this";
    }

    @Override
    public String toString() {
        return typeInfo.simpleName + (writeSuper ? ".super" : ".this");
    }

    @Override
    public String detailedString() {
        return (writeSuper ? "super" : "this") + " keyword (of " + typeInfo.fullyQualifiedName + ")";
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return SideEffect.LOCAL;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        boolean b = explicit && explicitlyWriteType;
        if (writeSuper) {
            return UpgradableBooleanMap.of(typeInfo, b, typeInfo.typeInspection.get().parentClass.bestTypeInfo(), false);
        }
        return UpgradableBooleanMap.of(typeInfo, b);
    }
}
