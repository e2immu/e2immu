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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.*;

public class Cast implements Expression {
    public final Expression expression;
    public final ParameterizedType parameterizedType;

    public Cast(@NotNull Expression expression, @NotNull ParameterizedType parameterizedType) {
        this.expression = Objects.requireNonNull(expression);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new Cast(translationMap.translateExpression(expression), translationMap.translateType(parameterizedType));
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        // for now, casting will simply keep the value
        return expression.evaluate(evaluationContext, visitor, forwardEvaluationInfo);
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public String expressionString(int indent) {
        return "(" + parameterizedType.stream() + ")" + bracketedExpressionString(indent, expression);
    }

    @Override
    public int precedence() {
        return 13;
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
