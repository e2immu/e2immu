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

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.annotation.NotNull;

import java.util.Objects;

// see ExpressionWithMethodReferenceResolution, try to do something similar

public abstract class VariableWithConcreteReturnType implements Variable {

    public final ParameterizedType concreteReturnType;

    protected VariableWithConcreteReturnType(@NotNull ParameterizedType concreteReturnType) {
        this.concreteReturnType = Objects.requireNonNull(concreteReturnType);
    }

    @Override
    public ParameterizedType concreteReturnType() {
        return concreteReturnType;
    }

    @Override
    public String toString() {
        return fullyQualifiedName();
    }
}