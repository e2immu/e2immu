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

package org.e2immu.analyser.model.expression;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record CommaExpression(List<Expression> expressions) implements Expression {

    public CommaExpression {
        assert expressions.size() > 1;
    }

    public static Expression comma(EvaluationContext evaluationContext, List<Expression> input) {
        List<Expression> expressions = input.stream()
                .filter(e -> !(e instanceof ConstantExpression))
                .collect(Collectors.toUnmodifiableList());
        if (expressions.size() == 0) return new BooleanConstant(evaluationContext.getPrimitives(), true);
        if (expressions.size() == 1) return expressions.get(0);
        if (expressions.stream().anyMatch(Expression::isUnknown)) throw new UnsupportedOperationException();
        return new CommaExpression(ImmutableList.copyOf(expressions));
    }

    @Override
    public OutputBuilder output() {
        return expressions.stream().map(Expression::output).collect(OutputBuilder.joining(Symbol.COMMA));
    }

    @Override
    public ParameterizedType returnType() {
        return expressions.get(expressions.size() - 1).returnType();
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        for (Expression expression : expressions) {
            EvaluationResult result = expression.evaluate(evaluationContext, forwardEvaluationInfo);
            builder.compose(result);
        }
        // as we compose, the value of the last result survives, earlier ones are discarded
        return builder.build();
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        List<Expression> newExpressions = new ArrayList<>(this.expressions.size());
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        for (Expression expression : this.expressions) {
            EvaluationResult result = expression.reEvaluate(evaluationContext, translation);
            newExpressions.add(result.getExpression());
            builder.compose(result);
        }
        Expression newComma = CommaExpression.comma(evaluationContext, newExpressions);
        return builder.setExpression(newComma).build();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new CommaExpression(expressions.stream().map(translationMap::translateExpression).collect(Collectors.toUnmodifiableList()));
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return expressions.get(expressions.size() - 1).getObjectFlow();
    }
}
