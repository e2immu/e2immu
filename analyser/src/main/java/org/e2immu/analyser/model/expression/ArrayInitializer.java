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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.E2Container;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@E2Container
public class ArrayInitializer extends ElementImpl implements Expression {

    public final MultiExpression multiExpression;
    private final ParameterizedType commonType;
    private final InspectionProvider inspectionProvider;

    public ArrayInitializer(InspectionProvider inspectionProvider,
                            List<Expression> values,
                            ParameterizedType formalCommonType) {
        super(Identifier.generate());
        this.multiExpression = MultiExpression.create(values);
        this.commonType = formalCommonType.commonType(inspectionProvider, multiExpression.commonType(inspectionProvider));
        this.inspectionProvider = inspectionProvider;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        List<EvaluationResult> reClauseERs = multiExpression.stream()
                .map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        List<Expression> reValues = reClauseERs.stream().map(EvaluationResult::value).collect(Collectors.toList());
        return new EvaluationResult.Builder()
                .compose(reClauseERs)
                .setExpression(new ArrayInitializer(evaluationContext.getAnalyserContext(), reValues, commonType))
                .build();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new ArrayInitializer(inspectionProvider, multiExpression.stream().map(translationMap::translateExpression)
                .collect(Collectors.toList()), translationMap.translateType(commonType));
    }

    @Override
    public ParameterizedType returnType() {
        return commonType;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder()
                .add(Symbol.LEFT_BRACE)
                .add(multiExpression.stream().map(expression -> expression.output(qualification))
                        .collect(OutputBuilder.joining(Symbol.COMMA)))
                .add(Symbol.RIGHT_BRACE);
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }


    @Override
    public List<? extends Element> subElements() {
        return List.of(multiExpression.expressions());
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext,
                                     ForwardEvaluationInfo forwardEvaluationInfo) {
        List<EvaluationResult> results = multiExpression.stream()
                .map(e -> e.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT))
                .collect(Collectors.toList());
        List<Expression> values = results.stream().map(EvaluationResult::getExpression).collect(Collectors.toList());

        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(results);
        builder.setExpression(new ArrayInitializer(evaluationContext.getAnalyserContext(), values, commonType));

        return builder.build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_ARRAY;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return Arrays.compare(multiExpression.expressions(), ((ArrayInitializer) v).multiExpression.expressions());
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        if (VariableProperty.NOT_NULL_EXPRESSION == variableProperty) {
            int notNull = multiExpression.getProperty(evaluationContext, variableProperty, duringEvaluation);
            if (notNull == Level.DELAY) return Level.DELAY;
            return MultiLevel.composeOneLevelMore(notNull);
        }
        if (VariableProperty.EXTERNAL_IMMUTABLE == variableProperty || VariableProperty.IMMUTABLE == variableProperty) {
            // it is an array
            return MultiLevel.EFFECTIVELY_E1IMMUTABLE;
        }
        // default is to refer to each of the components
        return multiExpression.getProperty(evaluationContext, variableProperty, duringEvaluation);
    }

    @Override
    public List<Variable> variables() {
        return multiExpression.variables();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayInitializer that = (ArrayInitializer) o;
        return Arrays.equals(multiExpression.expressions(), that.multiExpression.expressions());
    }

    @Override
    public int hashCode() {
        return Objects.hash((Object[]) multiExpression.expressions());
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            multiExpression.stream().forEach(v -> v.visit(predicate));
        }
    }
}
