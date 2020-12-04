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
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.output.OutputBuilder;

import java.util.Objects;

public class ReturnVariable implements Variable {
    public final ParameterizedType returnType;
    public final String simpleName;
    public final String fqn;

    public ReturnVariable(MethodInfo methodInfo) {
        this.returnType = methodInfo.returnType();
        simpleName = methodInfo.name;
        fqn = methodInfo.fullyQualifiedName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReturnVariable that = (ReturnVariable) o;
        return returnType.equals(that.returnType) &&
                fqn.equals(that.fqn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(returnType, fqn);
    }

    @Override
    public ParameterizedType concreteReturnType() {
        return returnType;
    }

    @Override
    public ParameterizedType parameterizedType() {
        return returnType;
    }

    @Override
    public String simpleName() {
        return "return " + simpleName;
    }

    @Override
    public String fullyQualifiedName() {
        return fqn;
    }

    @Override
    public OutputBuilder output() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return simpleName;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return null;
    }
}
