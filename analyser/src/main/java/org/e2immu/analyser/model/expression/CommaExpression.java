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
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.expression.util.TranslationCollectors;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.function.Predicate;

public class CommaExpression extends BaseExpression implements Expression {

    private final List<Expression> expressions;

    public CommaExpression(List<Expression> expressions) {
        super(Identifier.joined("comma expression", expressions.stream().map(Expression::getIdentifier).toList()),
                1 + expressions.stream().mapToInt(Expression::getComplexity).sum());
        this.expressions = expressions;
    }

    public static Expression comma(EvaluationResult context, List<Expression> input) {
        List<Expression> expressions = input.stream().filter(e -> !e.isConstant()).toList();
        if (expressions.size() == 0) return new BooleanConstant(context.getPrimitives(), true);
        if (expressions.size() == 1) return expressions.get(0);
        if (expressions.stream().anyMatch(Expression::isEmpty)) throw new UnsupportedOperationException();
        return new CommaExpression(List.copyOf(expressions));
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return expressions.stream().map(expression -> expression.output(qualification)).collect(OutputBuilder.joining(Symbol.COMMA));
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            expressions.forEach(e -> e.visit(predicate));
        }
    }

    @Override
    public void visit(Visitor visitor) {
        if (visitor.beforeExpression(this)) {
            expressions.forEach(e -> e.visit(visitor));
        }
        visitor.afterExpression(this);
    }

    @Override
    public ParameterizedType returnType() {
        return expressions.get(expressions.size() - 1).returnType();
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }

    /*
    Code is more complicated than expected because we do not always want to compute the expressions in an
    incrementally growing EvaluationResult. The effect of i++ in for-loops should be "forgotten", see Range_3, _4.
    We implement this by wrapping the updater of loop constructs in a PropertyWrapper with a marker property.
     */
    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context);
        EvaluationResultImpl.Builder builderForIncrementalResult = new EvaluationResultImpl.Builder(context);
        int count = 0;
        for (Expression expression : expressions) {
            ForwardEvaluationInfo fwd = count == expressions.size() - 1 ? forwardEvaluationInfo : ForwardEvaluationInfo.DEFAULT;
            EvaluationResult incrementalResult = builderForIncrementalResult.build();

            boolean clearIncremental = expression instanceof PropertyWrapper pw && pw.hasProperty(Property.MARK_CLEAR_INCREMENTAL);
            Expression e = clearIncremental ? ((PropertyWrapper) expression).expression() : expression;

            EvaluationResult result = e.evaluate(incrementalResult, fwd);
            builder.composeStore(result);

            if (clearIncremental) {
                builderForIncrementalResult = new EvaluationResultImpl.Builder(context);
            } else {
                builderForIncrementalResult.compose(result);
            }
            count++;
        }
        // as we compose, the value of the last result survives, earlier ones are discarded
        return builder.build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_COMMA;
    }

    @Override
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError{
        if (v instanceof CommaExpression ce) {
            return ListUtil.compare(expressions, ce.expressions);
        }
        throw new ExpressionComparator.InternalError();
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;

        List<Expression> translatedExpressions = expressions.stream()
                .map(e -> e.translate(inspectionProvider, translationMap))
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
    public LinkedVariables linkedVariables(EvaluationResult context) {
        return expressions.stream().map(e -> e.linkedVariables(context)).reduce(LinkedVariables.EMPTY, LinkedVariables::merge);
    }

    @Override
    public List<Variable> variables(DescendMode descendIntoFieldReferences) {
        return expressions.stream().flatMap(e -> e.variables(descendIntoFieldReferences).stream()).toList();
    }

    @Override
    public String toString() {
        return expressions.toString();
    }
}
