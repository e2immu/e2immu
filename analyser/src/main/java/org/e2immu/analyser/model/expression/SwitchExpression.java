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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.inspector.expr.ParseSwitchExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.SwitchEntry;
import org.e2immu.analyser.model.statement.YieldStatement;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.Primitives;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record SwitchExpression(Expression selector,
                               List<SwitchEntry> switchEntries,
                               ParameterizedType returnType,
                               MultiExpression yieldExpressions) implements Expression, HasSwitchLabels {

    public SwitchExpression {
        switchEntries.forEach(e -> {
            Objects.requireNonNull(e.switchVariableAsExpression);
            Objects.requireNonNull(e.labels);
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SwitchExpression that = (SwitchExpression) o;
        return selector.equals(that.selector) && switchEntries.equals(that.switchEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selector, switchEntries);
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        Guide.GuideGenerator blockGenerator = Guide.generatorForBlock();
        return new OutputBuilder().add(new Text("switch"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(selector.output(qualification))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(switchEntries.stream().map(switchEntry ->
                        switchEntry.output(qualification, blockGenerator, null))
                        .collect(OutputBuilder.joining(Space.ONE_IS_NICE_EASY_SPLIT, Symbol.LEFT_BRACE,
                                Symbol.RIGHT_BRACE, blockGenerator)));
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return Precedence.TERNARY; // TODO verify this is correct
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        EvaluationResult selectorResult = selector.evaluate(evaluationContext, forwardEvaluationInfo);

        Expression selectorValue = selectorResult.value();
        if (selectorValue.isConstant()) {
            // do some short-cuts
        }
        List<Expression> newYieldExpressions = new ArrayList<>(yieldExpressions.expressions().length);
        for (SwitchEntry switchEntry : switchEntries) {
            if (switchEntry.structure.statements().size() == 1) {
                // single expression
                Expression condition = convertDefaultToNegationOfAllOthers(evaluationContext, switchEntry.structure.expression());
                EvaluationContext localContext = evaluationContext.child(condition);
                EvaluationResult entryResult;
                Statement statement = switchEntry.structure.statements().get(0);
                Expression expression = statement instanceof YieldStatement yieldStatement ? yieldStatement.expression
                        : statement instanceof ExpressionAsStatement eas ? eas.expression : null;
                if (expression == null) throw new UnsupportedOperationException("??");
                entryResult = expression.evaluate(localContext, forwardEvaluationInfo);
                builder.composeIgnoreExpression(entryResult);
                newYieldExpressions.add(entryResult.getExpression());
            } else {
                List<Expression> yields = ParseSwitchExpr.extractYields(switchEntry.structure.statements());
                newYieldExpressions.addAll(yields); // FIXME how do we go about evaluating?
            }
        }
        builder.compose(selectorResult);
        builder.setExpression(new SwitchExpression(selectorValue, switchEntries, returnType, MultiExpression.create(newYieldExpressions)));
        return builder.build();
    }

    private Expression convertDefaultToNegationOfAllOthers(EvaluationContext evaluationContext, Expression expression) {
        if (!(expression instanceof EmptyExpression)) return expression;
        return new And(evaluationContext.getPrimitives()).append(evaluationContext,
                switchEntries.stream().flatMap(se -> se.labels.stream()).map(label ->
                        Negation.negate(evaluationContext, Equals.equals(evaluationContext, label, selector)))
                        .toArray(Expression[]::new));
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Stream<Expression> labels() {
        return switchEntries.stream().flatMap(e -> e.labels.stream());
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        if (Primitives.isPrimitiveExcludingVoid(returnType)) {
            return UnknownExpression.primitiveGetProperty(variableProperty);
        }
        return yieldExpressions.getProperty(evaluationContext, variableProperty, duringEvaluation);
    }
}
