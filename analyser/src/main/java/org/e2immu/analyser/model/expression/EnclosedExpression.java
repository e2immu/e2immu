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

import org.e2immu.analyser.model.*;

import java.util.List;

public class EnclosedExpression implements Expression {
    public final Expression inner;

    public EnclosedExpression(Expression inner) {
        this.inner = inner;
    }

    @Override
    public ParameterizedType returnType() {
        return inner.returnType();
    }

    @Override
    public String expressionString(int indent) {
        return "(" + inner.expressionString(indent) + ")";
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    public List<Expression> subExpressions() {
        return List.of(inner);
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        return inner.evaluate(evaluationContext, visitor, forwardEvaluationInfo);
    }
}
