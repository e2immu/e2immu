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
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.List;
import java.util.Objects;

public class LocalVariableReference extends VariableWithConcreteReturnType {
    public final LocalVariable variable;
    public final List<Expression> assignmentExpressions;

    public LocalVariableReference(InspectionProvider inspectionProvider,
                                  LocalVariable localVariable,
                                  List<Expression> assignmentExpressions) {
        super(assignmentExpressions.isEmpty() ? localVariable.parameterizedType :
                localVariable.parameterizedType.fillTypeParameters(inspectionProvider,
                        assignmentExpressions.get(0).returnType()));
        this.variable = Objects.requireNonNull(localVariable);
        this.assignmentExpressions = Objects.requireNonNull(assignmentExpressions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalVariableReference that = (LocalVariableReference) o;
        return variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable);
    }

    @Override
    public ParameterizedType parameterizedType() {
        return variable.parameterizedType;
    }

    @Override
    public String simpleName() {
        return variable.name;
    }

    @Override
    public String fullyQualifiedName() {
        return variable.name;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return assignmentExpressions.stream().map(e -> e.sideEffect(evaluationContext)).reduce(SideEffect.LOCAL, SideEffect::combine);
    }

    @Override
    public boolean isLocal() {
        return true;
    }
}
