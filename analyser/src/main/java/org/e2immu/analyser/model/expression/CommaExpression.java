/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.TranslationCollectors;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CommaExpression extends BaseExpression implements Expression {

    private final List<Expression> expressions;

    public CommaExpression(List<Expression> expressions) {
        super(Identifier.generate());
        assert expressions.size() > 1;
        this.expressions = expressions;
    }

    public static Expression comma(EvaluationContext evaluationContext, List<Expression> input) {
        List<Expression> expressions = input.stream().filter(e -> !(e instanceof ConstantExpression)).toList();
        if (expressions.size() == 0) return new BooleanConstant(evaluationContext.getPrimitives(), true);
        if (expressions.size() == 1) return expressions.get(0);
        if (expressions.stream().anyMatch(Expression::isEmpty)) throw new UnsupportedOperationException();
        return new CommaExpression(List.copyOf(expressions));
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return expressions.stream().map(expression -> expression.output(qualification)).collect(OutputBuilder.joining(Symbol.COMMA));
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
        int count = 0;
        for (Expression expression : expressions) {
            ForwardEvaluationInfo fwd = count == expressions.size() - 1 ? forwardEvaluationInfo : ForwardEvaluationInfo.DEFAULT;
            EvaluationResult result = expression.evaluate(evaluationContext, fwd);
            builder.composeStore(result);
            count++;
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
        List<Expression> translatedExpressions = expressions.stream().map(e -> e.translate(translationMap))
                .collect(TranslationCollectors.toList(expressions));
        if (translatedExpressions == expressions) return this;
        return new CommaExpression(expressions);
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return expressions.stream().map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        return new CommaExpression(expressions.stream().map(e -> e.isDelayed() ? e.mergeDelays(causesOfDelay) : e).toList());
    }

    public List<Expression> expressions() {
        return expressions;
    }

    @Override
    public String toString() {
        return expressions.toString();
    }
}
