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
import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

@E2Container
public record ArrayLengthExpression(Primitives primitives,
                                    Expression scope) implements Expression {

    public ArrayLengthExpression(Primitives primitives,
                                 @NotNull Expression scope) {
        this.scope = Objects.requireNonNull(scope);
        this.primitives = primitives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayLengthExpression that = (ArrayLengthExpression) o;
        return scope.equals(that.scope);
    }

    @Override
    public boolean hasBeenEvaluated() {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new ArrayLengthExpression(primitives, translationMap.translateExpression(scope));
    }

    @Override
    public int order() {
        return 0;
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
    public List<? extends Element> subElements() {
        return List.of(scope);
    }

    @Override
    public ParameterizedType returnType() {
        return primitives.intParameterizedType;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output() {
        return new OutputBuilder().add(outputInParenthesis(precedence(), scope)).add(Symbol.DOT).add(new Text("length"));
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult result = scope.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(result);

        if (result.value instanceof ArrayInitializer arrayInitializer) {
            Expression size = new IntConstant(evaluationContext.getPrimitives(), arrayInitializer.multiExpression.expressions().length,
                    ObjectFlow.NO_FLOW);
            builder.setExpression(size);
        } else {
            builder.setExpression(PrimitiveExpression.PRIMITIVE_EXPRESSION);
        }
        return builder.build();
    }
}
