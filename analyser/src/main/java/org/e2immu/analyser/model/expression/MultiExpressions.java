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
import org.e2immu.analyser.model.impl.BaseExpression;
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
Multiple expressions to be evaluated, yet only the last one determines type, properties, etc.
Used to reduce complexity.
 */
@E2Container
public class MultiExpressions extends BaseExpression implements Expression {

    public final MultiExpression multiExpression;
    private final InspectionProvider inspectionProvider;

    public MultiExpressions(Identifier identifier,
                            InspectionProvider inspectionProvider,
                            MultiExpression multiExpression) {
        super(identifier);
        assert multiExpression.expressions().length > 0;
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
                .setExpression(new MultiExpressions(identifier, evaluationContext.getAnalyserContext(), reMulti))
                .build();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new MultiExpressions(identifier, inspectionProvider, new MultiExpression(multiExpression.stream()
                .map(translationMap::translateExpression).toArray(Expression[]::new)));
    }

    @Override
    public ParameterizedType returnType() {
        return multiExpression.lastExpression().returnType();
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    public OutputBuilder output(Qualification qualification) {
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
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        for (Expression expression : multiExpression.expressions()) {
            EvaluationResult result = expression.evaluate(evaluationContext, forwardEvaluationInfo);
            builder.compose(result);
        }
        return builder.build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_ARRAY;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return Arrays.compare(multiExpression.expressions(), ((MultiExpressions) v).multiExpression.expressions());
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        Expression last = multiExpression.lastExpression();
        return last.getProperty(evaluationContext, property, duringEvaluation);
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return Arrays.stream(multiExpression.expressions())
                .flatMap(e -> e.variables(descendIntoFieldReferences).stream()).toList();
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return multiExpression.lastExpression().causesOfDelay();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MultiExpressions that = (MultiExpressions) o;
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