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
import org.e2immu.analyser.model.expression.util.TranslationCollectors;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class Or extends ExpressionCanBeTooComplex {
    private static final Logger LOGGER = LoggerFactory.getLogger(Or.class);

    private final Primitives primitives;
    private final List<Expression> expressions;
    public static final int COMPLEXITY = 4;

    public Or(Primitives primitives, List<Expression> expressions) {
        this(Identifier.joined("or", expressions.stream().map(Expression::getIdentifier).toList()), primitives, expressions);
    }

    public Or(Identifier identifier, Primitives primitives, List<Expression> expressions) {
        super(identifier, COMPLEXITY + expressions.stream().mapToInt(Expression::getComplexity).sum());
        this.primitives = Objects.requireNonNull(primitives);
        this.expressions = Objects.requireNonNull(expressions);
    }
    // testing only

    private Or(Identifier identifier, Primitives primitives) {
        this(identifier, primitives, List.of());
    }

    public static Expression or(EvaluationResult context, Expression... values) {
        Identifier id = Identifier.joined("or", Arrays.stream(values).map(Expression::getIdentifier).toList());
        return new Or(id, context.getPrimitives()).append(context, values);
    }

    public static Expression or(EvaluationResult context, List<Expression> values) {
        Identifier id = Identifier.joined("or", values.stream().map(Expression::getIdentifier).toList());
        return new Or(id, context.getPrimitives()).append(context, values);
    }

    private Expression append(EvaluationResult context, Expression... values) {
        return append(context, Arrays.asList(values));
    }

    // we try to maintain a CNF
    private Expression append(EvaluationResult context, List<Expression> values) {

        // STEP 1: trivial reductions

        if (this.expressions.isEmpty() && values.size() == 1) {
            if (values.get(0) instanceof Or || values.get(0) instanceof And) {
                LOGGER.debug("Return immediately in Or: {}", values.get(0));
                return values.get(0);
            }
        }

        // STEP 2: concat everything

        ArrayList<Expression> concat = new ArrayList<>(values.size() + this.expressions.size());
        concat.addAll(this.expressions);
        recursivelyAdd(concat, values);

        // STEP 3: loop

        And firstAnd = null;

        int complexity = values.stream().mapToInt(Expression::getComplexity).sum();
        boolean changes = complexity < context.evaluationContext().limitOnComplexity();
        if (!changes) {
            LOGGER.debug("Not analysing OR operation, complexity {}", complexity);
            return reducedComplexity(context, expressions, values.toArray(Expression[]::new));
        }
        assert complexity < Expression.HARD_LIMIT_ON_COMPLEXITY : "Complexity reached " + complexity;

        while (changes) {
            changes = false;

            // STEP 4a: sort

            Collections.sort(concat);

            // STEP 4b: observations

            for (Expression value : concat) {
                if (value instanceof BooleanConstant bc && bc.constant()) {
                    LOGGER.debug("Return TRUE in Or, found TRUE");
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
                    LOGGER.debug("Return TRUE in Or, found opposites {}", value);
                    return new BooleanConstant(primitives, true);
                }

                // A || A
                if (value.equals(prev)) {
                    changes = true;
                } else if (value instanceof And andValue) {
                    if (andValue.getExpressions().size() == 1) {
                        newConcat.add(andValue.getExpressions().get(0));
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
            Expression[] components = firstAnd.getExpressions().stream()
                    .map(v -> append(context, ListUtil.immutableConcat(finalValues, List.of(v))))
                    .toArray(Expression[]::new);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Found And-clause {} in {}, components for new And are {}", firstAnd, this, Arrays.toString(components));
            }
            int complexityComponents = Arrays.stream(components).mapToInt(Expression::getComplexity).sum();
            if (complexityComponents < context.evaluationContext().limitOnComplexity()) {
                return And.and(context, components);
            }
        }
        if (finalValues.size() == 1) return finalValues.get(0);

        for (Expression value : finalValues) {
            if (value.isEmpty()) throw new UnsupportedOperationException();
        }

        if (finalValues.isEmpty()) {
            LOGGER.debug("Empty disjunction returned as false");
            return new BooleanConstant(primitives, false);
        }
        Identifier id = Identifier.joined("or", finalValues.stream().map(Expression::getIdentifier).toList());
        return new Or(id, primitives, finalValues);
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
        return primitives.booleanParameterizedType();
    }

    @Override
    public Precedence precedence() {
        return Precedence.LOGICAL_OR;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult[] clauseResults = expressions.stream()
                .map(v -> v.evaluate(context, forwardEvaluationInfo)).toArray(EvaluationResult[]::new);
        Expression[] clauses = Arrays.stream(clauseResults).map(EvaluationResult::value).toArray(Expression[]::new);
        Identifier id = Identifier.joined("or", Arrays.stream(clauses).map(Expression::getIdentifier).toList());
        Expression or = new Or(id, primitives).append(context, clauses);
        return new EvaluationResult.Builder(context).compose(clauseResults).setExpression(or).build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_OR;
    }

    @Override
    public int internalCompareTo(Expression v) {
        if (v instanceof InlineConditional inlineConditional) {
            return expressions.get(0).compareTo(inlineConditional.condition);
        }
        Or orValue = (Or) v;
        return ListUtil.compare(expressions, orValue.expressions);
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return expressions.stream().flatMap(v -> v.variables(descendIntoFieldReferences).stream()).collect(Collectors.toList());
    }

    // no implementation of the filters

    @Override
    public EvaluationResult reEvaluate(EvaluationResult context, Map<Expression, Expression> translation, ForwardReEvaluationInfo forwardReEvaluationInfo) {
        List<EvaluationResult> reClauseERs = expressions.stream()
                .map(v -> v.reEvaluate(context, translation, forwardReEvaluationInfo))
                .collect(Collectors.toList());
        Expression[] reClauses = reClauseERs.stream().map(EvaluationResult::value).toArray(Expression[]::new);
        Identifier id = Identifier.joined("and", Arrays.stream(reClauses).map(Expression::getIdentifier).toList());
        Expression or = new Or(id, primitives).append(context, reClauses);
        return new EvaluationResult.Builder(context).compose(reClauseERs).setExpression(or).build();
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return expressions.stream().map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        return new Or(identifier, primitives, expressions.stream()
                .map(e -> e.isDelayed() ? e.mergeDelays(causesOfDelay) : e)
                .toList());
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expressions.forEach(v -> v.visit(predicate));
        }
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return getPropertyForPrimitiveResults(property);
    }

    @Override
    public List<? extends Element> subElements() {
        return expressions;
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Expression> translated = expressions.isEmpty() ? expressions :
                expressions.stream().map(e -> e.translate(inspectionProvider, translationMap))
                        .collect(TranslationCollectors.toList(expressions));
        if (expressions == translated) return this;
        return new Or(identifier, primitives, translated);
    }

    public Identifier identifier() {
        return identifier;
    }

    public Primitives primitives() {
        return primitives;
    }

    public List<Expression> expressions() {
        return expressions;
    }

}
