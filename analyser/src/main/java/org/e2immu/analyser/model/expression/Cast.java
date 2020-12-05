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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Objects;

public record Cast(Expression expression,
                   ParameterizedType parameterizedType) implements Expression {

    public Cast(Expression expression, ParameterizedType parameterizedType) {
        this.expression = Objects.requireNonNull(expression);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cast cast = (Cast) o;
        return expression.equals(cast.expression) &&
                parameterizedType.equals(cast.parameterizedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, parameterizedType);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new Cast(translationMap.translateExpression(expression), translationMap.translateType(parameterizedType));
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public boolean hasBeenEvaluated() {
        return false;
    }

    @Override
    public NewObject getInstance(EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NYE;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        // for now, casting will simply keep the value
        // TODO should we not wrap in PropertyWrapper, with explicit type?
        return expression.evaluate(evaluationContext, forwardEvaluationInfo);
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public OutputBuilder output() {
        return new OutputBuilder().add(Symbol.LEFT_PARENTHESIS).add(parameterizedType.output()).add(Symbol.RIGHT_PARENTHESIS)
                .add(outputInParenthesis(precedence(), expression));
    }

    @Override
    public Precedence precedence() {
        return Precedence.UNARY;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(expression.typesReferenced(), parameterizedType.typesReferenced(true));
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }
}
