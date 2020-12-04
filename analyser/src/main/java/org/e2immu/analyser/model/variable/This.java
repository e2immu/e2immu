/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model.variable;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.VariableName;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.Objects;

/**
 * Variable representing the "this" keyword
 */
public class This implements Variable {
    public final TypeInfo typeInfo;
    public final boolean explicitlyWriteType;
    public final boolean writeSuper;
    public final ParameterizedType typeAsParameterizedType;

    public This(InspectionProvider inspectionProvider, TypeInfo typeInfo) {
        this(inspectionProvider, typeInfo, false, false);
    }

    public This(InspectionProvider inspectionProvider, TypeInfo typeInfo, boolean explicitlyWriteType, boolean writeSuper) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.explicitlyWriteType = explicitlyWriteType;
        this.writeSuper = writeSuper;
        typeAsParameterizedType = typeInfo.asParameterizedType(inspectionProvider);
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
        return typeAsParameterizedType;
    }

    @Override
    public ParameterizedType concreteReturnType() {
        return typeAsParameterizedType;
    }

    @Override
    public String simpleName() {
        if (explicitlyWriteType) return typeInfo.simpleName + (writeSuper ? ".super" : ".this");
        return writeSuper ? "super" : "this";
    }

    @Override
    public OutputBuilder output() {
        return new OutputBuilder().add(new VariableName(writeSuper ? "super" : "this", typeInfo, VariableName.Nature.STATIC));
    }

    @Override
    public String toString() {
        return output().toString();
    }

    @Override
    public String fullyQualifiedName() {
        return typeInfo.fullyQualifiedName + "." + (writeSuper ? "super" : "this");
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
            return UpgradableBooleanMap.of(typeInfo, b, typeInfo.typeInspection.get().parentClass().bestTypeInfo(), false);
        }
        return UpgradableBooleanMap.of(typeInfo, b);
    }
}
