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
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.EXPRESSION;
import static org.e2immu.analyser.util.Logger.log;

public record Or(Primitives primitives, List<Expression> expressions) implements Expression {

    public Or {
        Objects.requireNonNull(primitives);
        Objects.requireNonNull(expressions);
    }

    // testing only
    public Or(Primitives primitives) {
        this(primitives, List.of());
    }

    public Expression append(EvaluationContext evaluationContext, Expression... values) {
        return append(evaluationContext, Arrays.asList(values));
    }

    // we try to maintain a CNF
    public Expression append(EvaluationContext evaluationContext, List<Expression> values) {

        // STEP 1: trivial reductions

        if (this.expressions.isEmpty() && values.size() == 1) {
            if (values.get(0) instanceof Or || values.get(0) instanceof And) {
                log(EXPRESSION, "Return immediately in Or: {}", values.get(0));
                return values.get(0);
            }
        }

        // STEP 2: concat everything

        ArrayList<Expression> concat = new ArrayList<>(values.size() + this.expressions.size());
        concat.addAll(this.expressions);
        recursivelyAdd(concat, values);

        // STEP 3: loop

        And firstAnd = null;
        boolean changes = true;
        while (changes) {
            changes = false;

            // STEP 4a: sort

            Collections.sort(concat);

            // STEP 4b: observations

            for (Expression value : concat) {
                if (value instanceof BooleanConstant bc && bc.constant()) {
                    log(EXPRESSION, "Return TRUE in Or, found TRUE");
                    return new BooleanConstant(primitives, true);
                }
            }
            concat.removeIf(value -> value instanceof BooleanConstant); // FALSE can go

            // STEP 4c: reductions

            ArrayList<Expression> newConcat = new ArrayList<>(concat.size());
            Expression prev = null;
            for (Expression value : concat) {

                // this works because of sorting
                // A || !A will always sit next to each other
                if (value instanceof Negation ne && ne.expression.equals(prev)) {
                    log(EXPRESSION, "Return TRUE in Or, found opposites {}", value);
                    return new BooleanConstant(primitives, true);
                }

                // A || A
                if (value.equals(prev)) {
                    changes = true;
                } else if (value instanceof And andValue) {
                    if (andValue.expressions().size() == 1) {
                        newConcat.add(andValue.expressions().get(0));
                        changes = true;
                    } else if (firstAnd == null) {
                        firstAnd = andValue;
                        changes = true;
                    } else {
                        newConcat.add(andValue); // for later
                    }
                } else {
                    newConcat.add(value);
                }
                prev = value;
            }
            concat = newConcat;
        }
        ArrayList<Expression> finalValues = concat;
        if (firstAnd != null) {
            Expression[] components = firstAnd.expressions().stream()
                    .map(v -> append(evaluationContext, ListUtil.immutableConcat(finalValues, List.of(v))))
                    .toArray(Expression[]::new);
            log(EXPRESSION, "Found And-clause {} in {}, components for new And are {}", firstAnd, this, Arrays.toString(components));
            return new And(primitives).append(evaluationContext, components);
        }
        if (finalValues.size() == 1) return finalValues.get(0);

        for (Expression value : finalValues) {
            if (value.isUnknown()) throw new UnsupportedOperationException();
        }

        if (finalValues.isEmpty()) {
            log(EXPRESSION, "Empty disjuction returned as false");
            return new BooleanConstant(primitives, false);
        }

        return new Or(primitives, finalValues);
    }

    private void recursivelyAdd(ArrayList<Expression> concat, List<Expression> collect) {
        for (Expression value : collect) {
            if (value instanceof Or or) {
                concat.addAll(or.expressions);
            } else {
                concat.add(value);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Or orValue = (Or) o;
        return expressions.equals(orValue.expressions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expressions);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        Precedence precedence = precedence();
        return new OutputBuilder()
                .add(expressions.stream().map(e -> outputInParenthesis(qualification, precedence, e))
                        .collect(OutputBuilder.joining(Symbol.LOGICAL_OR)));
    }

    @Override
    public ParameterizedType returnType() {
        return primitives.booleanParameterizedType;
    }

    @Override
    public Precedence precedence() {
        return Precedence.LOGICAL_OR;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult[] clauseResults = expressions.stream()
                .map(v -> v.evaluate(evaluationContext, forwardEvaluationInfo)).toArray(EvaluationResult[]::new);
        Expression[] clauses = Arrays.stream(clauseResults).map(EvaluationResult::value).toArray(Expression[]::new);
        return new EvaluationResult.Builder()
                .compose(clauseResults)
                .setExpression(new Or(primitives).append(evaluationContext, clauses))
                .build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_OR;
    }

    @Override
    public int internalCompareTo(Expression v) {
        if(v instanceof InlineConditional inlineConditional) {
            return expressions.get(0).compareTo(inlineConditional.condition);
        }
        Or orValue = (Or) v;
        return ListUtil.compare(expressions, orValue.expressions);
    }

    @Override
    public List<Variable> variables() {
        return expressions.stream().flatMap(v -> v.variables().stream()).collect(Collectors.toList());
    }

    // no implementation of any of the filters

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        List<EvaluationResult> reClauseERs = expressions.stream()
                .map(v -> v.reEvaluate(evaluationContext, translation))
                .collect(Collectors.toList());
        Expression[] reClauses = reClauseERs.stream().map(EvaluationResult::value).toArray(Expression[]::new);
        return new EvaluationResult.Builder()
                .compose(reClauseERs)
                .setExpression(new Or(primitives).append(evaluationContext, reClauses))
                .build();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expressions.forEach(v -> v.visit(predicate));
        }
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        return UnknownExpression.primitiveGetProperty(variableProperty);
    }

    @Override
    public List<? extends Element> subElements() {
        return expressions;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        List<Expression> translated = expressions.stream().map(e -> e.translate(translationMap))
                .collect(Collectors.toUnmodifiableList());
        return new Or(primitives, translated);
    }

}
