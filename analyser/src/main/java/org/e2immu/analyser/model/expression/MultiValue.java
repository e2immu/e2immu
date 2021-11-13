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

import org.e2immu.analyser.analyser.*;
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

/*
A final field can have been initialised with multiple different values; in some situations
it pays to keep track of all of them.
 */
@E2Container
public class MultiValue extends ElementImpl implements Expression {

    public final MultiExpression multiExpression;
    private final ParameterizedType commonType;
    private final InspectionProvider inspectionProvider;

    public MultiValue(Identifier identifier,
                      InspectionProvider inspectionProvider,
                      MultiExpression multiExpression,
                      ParameterizedType formalCommonType) {
        super(identifier);
        this.commonType = formalCommonType.commonType(inspectionProvider, multiExpression.commonType(inspectionProvider));
        this.multiExpression = multiExpression;
        this.inspectionProvider = inspectionProvider;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        List<EvaluationResult> reClauseERs = multiExpression.stream().map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        Expression[] reValues = reClauseERs.stream().map(EvaluationResult::value).toArray(Expression[]::new);
        MultiExpression reMulti = new MultiExpression(reValues);
        return new EvaluationResult.Builder()
                .compose(reClauseERs)
                .setExpression(new MultiValue(identifier, evaluationContext.getAnalyserContext(), reMulti, commonType))
                .build();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new MultiValue(identifier, inspectionProvider, new MultiExpression(multiExpression.stream()
                .map(translationMap::translateExpression).toArray(Expression[]::new)), translationMap.translateType(commonType));
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
        // NOT part of standard java, this is an internal construct
        return new OutputBuilder()
                .add(Symbol.LEFT_BRACKET)
                .add(multiExpression.stream().map(expression -> expression.output(qualification))
                        .collect(OutputBuilder.joining(Symbol.COMMA)))
                .add(Symbol.RIGHT_BRACKET);
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
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_ARRAY;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return Arrays.compare(multiExpression.expressions(), ((MultiValue) v).multiExpression.expressions());
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        if (VariableProperty.NOT_NULL_EXPRESSION == variableProperty) {
            DV notNull = multiExpression.getProperty(evaluationContext, variableProperty, duringEvaluation);
            if (notNull.isDelayed()) return notNull;
            return MultiLevel.composeOneLevelLessNotNull(notNull); // default = @NotNull level 0
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
        MultiValue that = (MultiValue) o;
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
