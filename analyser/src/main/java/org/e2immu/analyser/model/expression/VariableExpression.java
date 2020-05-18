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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.ErrorValue;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class VariableExpression implements Expression {
    public final Variable variable;

    public VariableExpression(@NotNull Variable variable) {
        this.variable = Objects.requireNonNull(variable);
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        Value value = evaluationContext.currentValue(variable);
        if (!forwardEvaluationInfo.isAssignmentTarget()) {
            evaluationContext.markRead(variable);
        }
        if (forwardEvaluationInfo.getNotNull() != Level.FALSE) {
            StatementAnalyser.variableOccursInNotNullContext(variable, evaluationContext, forwardEvaluationInfo.getNotNull());
        }
        if(forwardEvaluationInfo.getNotModified() >= Level.TRUE) {
            StatementAnalyser.markContentModified(evaluationContext, variable, forwardEvaluationInfo.getNotModified());
        }

        visitor.visit(this, evaluationContext, value);
        return value;
    }

    @Override
    public ParameterizedType returnType() {
        return variable.parameterizedType();
    }

    @Override
    public String expressionString(int indent) {
        return variable.name();
    }

    @Override
    public int precedence() {
        return 17;
    }

    @Override
    public List<Variable> variables() {
        return List.of(variable);
    }

    @Override
    public Optional<Variable> assignmentTarget() {
        return Optional.of(variable);
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return variable.sideEffect(Objects.requireNonNull(sideEffectContext));
    }
}
