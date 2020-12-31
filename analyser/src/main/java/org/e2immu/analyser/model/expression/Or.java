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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.CNF;
import static org.e2immu.analyser.util.Logger.log;

public record Or(Primitives primitives,
                 List<Expression> expressions,
                 ObjectFlow objectFlow) implements Expression {

    public Or {
        Objects.requireNonNull(primitives);
        Objects.requireNonNull(expressions);
        Objects.requireNonNull(objectFlow);
    }

    // testing only
    public Or(Primitives primitives) {
        this(primitives, List.of(), ObjectFlow.NO_FLOW);
    }

    public Or(Primitives primitives, ObjectFlow objectFlow) {
        this(primitives, List.of(), objectFlow);
    }


    public Expression append(EvaluationContext evaluationContext, Expression... values) {
        return append(evaluationContext, Arrays.asList(values));
    }

    // we try to maintain a CNF
    public Expression append(EvaluationContext evaluationContext, List<Expression> values) {

        // STEP 1: trivial reductions

        if (this.expressions.isEmpty() && values.size() == 1) {
            if (values.get(0) instanceof Or || values.get(0) instanceof And) {
                log(CNF, "Return immediately in Or: {}", values.get(0));
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
                    log(CNF, "Return TRUE in Or, found TRUE");
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
                    log(CNF, "Return TRUE in Or, found opposites {}", value);
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
            log(CNF, "Found And-clause {} in {}, components for new And are {}", firstAnd, this, Arrays.toString(components));
            return new And(primitives, objectFlow).append(evaluationContext, components);
        }
        if (finalValues.size() == 1) return finalValues.get(0);

        // FINAL STEP: check for unknowns (if there was a TRUE somewhere, we never get here)

        Expression unknown = null;
        for (Expression value : finalValues) {
            if (value.isUnknown()) {
                if (unknown == null) unknown = value;
                else unknown = unknown.combineUnknown(value);
            }
        }
        if (unknown != null) {
            log(CNF, "Return unknown value in Or, order " + unknown.order());
            return unknown;
        }

        if (finalValues.isEmpty()) {
            log(CNF, "Empty disjuction returned as false");
            return new BooleanConstant(primitives, false);
        }

        return new Or(primitives, finalValues, objectFlow);
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
    public OutputBuilder output() {
        Precedence precedence = precedence();
        return new OutputBuilder()
                .add(expressions.stream().map(e -> e.outputInParenthesis(precedence, e))
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
        return null;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_OR;
    }

    @Override
    public int internalCompareTo(Expression v) {
        Or orValue = (Or) v;
        return ListUtil.compare(expressions, orValue.expressions);
    }

    @Override
    public List<Variable> variables() {
        return expressions.stream().flatMap(v -> v.variables().stream()).collect(Collectors.toList());
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
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
                .setExpression(new Or(primitives, objectFlow).append(evaluationContext, reClauses))
                .build();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expressions.forEach(v -> v.visit(predicate));
        }
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return UnknownExpression.primitiveGetProperty(variableProperty);
    }
}
