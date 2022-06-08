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
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.SwitchEntry;
import org.e2immu.analyser.model.statement.ThrowStatement;
import org.e2immu.analyser.model.statement.YieldStatement;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
Our switch expressions do not have blocks! Blocks with yield get translated into if-statements in
a lambda, see ParseSwitchExpr.
 */
public class SwitchExpression extends BaseExpression implements Expression, HasSwitchLabels {

    private final Expression selector;
    private final List<SwitchEntry> switchEntries;
    private final ParameterizedType returnType;
    private final MultiExpression expressions;

    public SwitchExpression(Identifier identifier,
                            Expression selector,
                            List<SwitchEntry> switchEntries,
                            ParameterizedType returnType,
                            MultiExpression expressions) {
        super(identifier);
        switchEntries.forEach(e -> {
            Objects.requireNonNull(e.switchVariableAsExpression);
            Objects.requireNonNull(e.labels);
        });
        this.selector = Objects.requireNonNull(selector);
        this.switchEntries = switchEntries;
        this.returnType = Objects.requireNonNull(returnType);
        this.expressions = Objects.requireNonNull(expressions);
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
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        EvaluationResult selectorResult = selector.evaluate(context, forwardEvaluationInfo);

        Expression selectorValue = selectorResult.value();
        List<Expression> newYieldExpressions = new ArrayList<>(expressions.expressions().length);
        for (SwitchEntry switchEntry : switchEntries) {
            if (switchEntry.structure.statements() == null) {
                // block
                throw new UnsupportedOperationException();
            }
            if (switchEntry.structure.statements().size() == 1) {
                // single expression
                Expression condition = convertDefaultToNegationOfAllOthers(context, switchEntry.structure.expression());
                Set<Variable> conditionVariables = Stream.concat(condition.variables(true).stream(),
                        selector.variables(true).stream()).collect(Collectors.toUnmodifiableSet());
                EvaluationResult localContext = context.child(condition, conditionVariables);
                EvaluationResult entryResult;
                Statement statement = switchEntry.structure.statements().get(0);
                Expression expression = statement instanceof YieldStatement yieldStatement ? yieldStatement.expression
                        : statement instanceof ExpressionAsStatement eas ? eas.expression : null;
                if (expression != null) {
                    entryResult = expression.evaluate(localContext, forwardEvaluationInfo);
                    builder.composeIgnoreExpression(entryResult);
                    newYieldExpressions.add(entryResult.getExpression());
                } else {
                    assert statement instanceof ThrowStatement; // there is no expression to evaluate for a return value
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }
        builder.compose(selectorResult);
        builder.setExpression(new SwitchExpression(identifier,
                selectorValue, switchEntries, returnType, MultiExpression.create(newYieldExpressions)));
        return builder.build();
    }

    private Expression convertDefaultToNegationOfAllOthers(EvaluationResult context, Expression expression) {
        if (!(expression instanceof EmptyExpression)) return expression;
        return And.and(context, switchEntries.stream().flatMap(se -> se.labels.stream()).map(label ->
                        Negation.negate(context, Equals.equals(context, label, selector)))
                .toArray(Expression[]::new));
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<SwitchEntry> translatedSwitchEntries = switchEntries.stream()
                .map(se -> (SwitchEntry) se.translate(inspectionProvider, translationMap)).toList();
        MultiExpression translatedYieldExpressions = expressions.translate(inspectionProvider, translationMap);
        return new SwitchExpression(identifier, selector.translate(inspectionProvider, translationMap),
                translatedSwitchEntries, returnType, translatedYieldExpressions);
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
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return expressions.getProperty(context, property, duringEvaluation);
    }

    @Override
    public ParameterizedType returnType() {
        return returnType;
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return selector.causesOfDelay()
                .merge(Arrays.stream(expressions.expressions())
                        .map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge));
    }

    @Override
    public List<? extends Element> subElements() {
        return Stream.concat(Stream.of(selector), expressions.stream()).toList();
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            selector.visit(predicate);
            expressions.stream().forEach(v -> v.visit(predicate));
        }
    }
}
