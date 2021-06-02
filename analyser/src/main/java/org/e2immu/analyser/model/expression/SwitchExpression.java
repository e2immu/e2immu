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
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.HasSwitchLabels;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.statement.SwitchEntry;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.Primitives;

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
        builder.compose(selectorResult);
        builder.setExpression(new SwitchExpression(selectorValue, switchEntries, returnType, yieldExpressions));
        return builder.build();
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
