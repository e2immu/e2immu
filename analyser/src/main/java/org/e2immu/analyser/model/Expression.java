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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;

import java.util.List;

@E2Container
public interface Expression extends Element {

    @NotModified
    ParameterizedType returnType();

    @NotModified
    String expressionString(int indent);

    @NotModified
    int precedence();

    @NotModified
    EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo);

    @NotModified
    default String bracketedExpressionString(int indent, Expression expression) {
        if (expression.precedence() < precedence()) {
            return "(" + expression.expressionString(indent) + ")";
        }
        return expression.expressionString(indent);
    }

    @NotModified
    default List<LocalVariableReference> newLocalVariables() {
        return List.of();
    }

    @Override
    default Expression translate(TranslationMap translationMap) {
        return this;
    }

}
