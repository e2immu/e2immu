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
import org.e2immu.analyser.model.expression.util.InequalitySolver;
import org.e2immu.analyser.model.impl.BaseExpression;
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

public class And extends BaseExpression implements Expression {
    private final Primitives primitives;
    private final List<Expression> expressions;

    public And(Primitives primitives, List<Expression> expressions) {
        this(Identifier.generate(), primitives, expressions);
    }

    private And(Identifier identifier, Primitives primitives, List<Expression> expressions) {
        super(identifier);
        this.primitives = Objects.requireNonNull(primitives);
        this.expressions = Objects.requireNonNull(expressions);
    }

    private And(Primitives primitives) {
        this(Identifier.generate(), primitives, List.of());
    }

    private enum Action {
        SKIP, REPLACE, FALSE, TRUE, ADD, ADD_CHANGE
    }

    public static Expression and(EvaluationContext evaluationContext, Expression... values) {
        return new And(evaluationContext.getPrimitives()).append(evaluationContext, values);
    }

    // we try to maintain a CNF
    private Expression append(EvaluationContext evaluationContext, Expression... values) {

        // STEP 1: check that all values return boolean!
        for (Expression v : values) {
            if (v.isUnknown()) {
                throw new UnsupportedOperationException("Unknown value " + v + " in And");
            }
            if (v.returnType() == null) {
                throw new UnsupportedOperationException("Null return type for " + v + " in And");
            }
            if (v.returnType().isNotBooleanOrBoxedBoolean()) {
                throw new UnsupportedOperationException("Non-boolean return type for " + v + " in And: " + v.returnType());
            }
        }

        // STEP 2: trivial reductions

        if (this.expressions.isEmpty() && values.length == 1 && values[0] instanceof And) return values[0];

        // STEP 3: concat everything

        ArrayList<Expression> concat = new ArrayList<>(values.length + this.expressions.size());
        concat.addAll(this.expressions);
        recursivelyAdd(concat, Arrays.stream(values).collect(Collectors.toList()));

        // STEP 4: loop

        boolean changes = true;
        while (changes) {
            changes = false;

            // STEP 4a: sort

            Collections.sort(concat);

            // STEP 4b: observations

            for (Expression value : concat) {
                if (value instanceof BooleanConstant bc && !bc.constant()) {
                    log(EXPRESSION, "Return FALSE in And, found FALSE", value);
                    return new BooleanConstant(primitives, false);
                }
            }
            concat.removeIf(value -> value instanceof BooleanConstant); // TRUE can go

            // STEP 4c: reductions

            ArrayList<Expression> newConcat = new ArrayList<>(concat.size());
            Expression prev = null;
            int pos = 0;
            for (Expression value : concat) {

                Action action = analyse(evaluationContext, pos, newConcat, prev, value);
                switch (action) {
                    case FALSE:
                        return new BooleanConstant(primitives, false);
                    case TRUE:
                        return new BooleanConstant(primitives, true);
                    case ADD:
                        newConcat.add(value);
                        break;
                    case ADD_CHANGE:
                        newConcat.add(value);
                        changes = true;
                        break;
                    case REPLACE:
                        newConcat.set(newConcat.size() - 1, value);
                        changes = true;
                        break;
                    case SKIP:
                        changes = true;
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }

                prev = value;
                pos++;
            }
            concat = newConcat;
        }
        if (concat.isEmpty()) {
            log(EXPRESSION, "And reduced to 0 components, return true");
            return new BooleanConstant(primitives, true);
        }
        if (concat.size() == 1) {
            log(EXPRESSION, "And reduced to 1 component: {}", concat.get(0));
            return concat.get(0);
        }
        And res = new And(identifier, primitives, List.copyOf(concat));
        log(EXPRESSION, "Constructed {}", res);
        return res;
    }

    private Action analyse(EvaluationContext evaluationContext, int pos, ArrayList<Expression> newConcat,
                           Expression prev, Expression value) {
        // A && A
        if (value.equals(prev)) return Action.SKIP;

        // this works because of sorting
        // A && !A will always sit next to each other
        if (value instanceof Negation negatedValue && negatedValue.expression.equals(prev)) {
            log(EXPRESSION, "Return FALSE in And, found opposites for {}", value);
            return Action.FALSE;
        }

        // A && A ? B : C --> A && B
        if (value instanceof InlineConditional conditionalValue && conditionalValue.condition.equals(prev)) {
            newConcat.add(conditionalValue.ifTrue);
            return Action.SKIP;
        }
        // A ? B : C && !A --> !A && C
        if (prev instanceof InlineConditional conditionalValue &&
                conditionalValue.condition.equals(Negation.negate(evaluationContext, value))) {
            newConcat.set(newConcat.size() - 1, conditionalValue.ifFalse); // full replace
            return Action.ADD_CHANGE;
        }

        // A && (!A || ...) ==> we can remove the !A
        // if we keep doing this, the OrValue empties out, and we are in the situation:
        // A && !B && (!A || B) ==> each of the components of an OR occur in negative form earlier on
        // this is the more complicated form of A && !A
        if (value instanceof Or) {
            List<Expression> remaining = new ArrayList<>(components(value));
            Iterator<Expression> iterator = remaining.iterator();
            boolean changed = false;
            while (iterator.hasNext()) {
                Expression value1 = iterator.next();
                Expression negated1 = Negation.negate(evaluationContext, value1);
                boolean found = false;
                for (int pos2 = 0; pos2 < newConcat.size(); pos2++) {
                    if (pos2 != pos && negated1.equals(newConcat.get(pos2))) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    iterator.remove();
                    changed = true;
                }
            }
            if (changed) {
                if (remaining.isEmpty()) {
                    log(EXPRESSION, "Return FALSE in And, found opposite for {}", value);
                    return Action.FALSE;
                }
                // replace
                Expression orValue = Or.or(evaluationContext, remaining);
                newConcat.add(orValue);
                return Action.SKIP;
            }
        }

        // the more complicated variant of A && A => A
        // A && (A || xxx) ==> A
        if (value instanceof Or) {
            List<Expression> components = components(value);
            for (Expression value1 : components) {
                for (Expression value2 : newConcat) {
                    if (value1.equals(value2)) {
                        return Action.SKIP;
                    }
                }
            }
        }
        // A || B &&  A || !B ==> A
        if (value instanceof Or && prev instanceof Or) {
            List<Expression> components = components(value);
            List<Expression> prevComponents = components(prev);
            List<Expression> equal = new ArrayList<>();
            boolean ok = true;
            for (Expression value1 : components) {
                if (prevComponents.contains(value1)) {
                    equal.add(value1);
                } else if (!prevComponents.contains(Negation.negate(evaluationContext, value1))) {
                    // not opposite, e.g. C
                    ok = false;
                    break;
                }
            }
            if (ok && !equal.isEmpty()) {
                Expression orValue = Or.or(evaluationContext, equal);
                newConcat.set(newConcat.size() - 1, orValue);
                return Action.SKIP;
            }
        }

        // simplification of the OrValue

        if (value instanceof Or orValue) {
            if (orValue.expressions().size() == 1) {
                newConcat.add(orValue.expressions().get(0));
                return Action.SKIP;
            }
            return Action.ADD;
        }

        // combinations with equality and inequality (GE)

        if (value instanceof GreaterThanZero gt0 && gt0.expression().variables().size() > 1) {
            // it may be interesting to run the inequality solver
            InequalitySolver inequalitySolver = new InequalitySolver(evaluationContext, newConcat);
            Boolean resolve = inequalitySolver.evaluate(value);
            if (resolve == Boolean.FALSE) return Action.FALSE;
        }

        if (prev instanceof Negation negatedPrev && negatedPrev.expression instanceof Equals ev1) {
            if (value instanceof Equals ev2) {
                // not (3 == a) && (4 == a)  (the situation 3 == a && not (3 == a) has been solved as A && not A == False
                if (ev1.rhs.equals(ev2.rhs) && !ev1.lhs.equals(ev2.lhs)) {
                    newConcat.remove(newConcat.size() - 1); // full replace
                    return Action.ADD;
                }
            }
        }

        if (prev instanceof Equals ev1) {
            if (value instanceof Equals ev2) {
                // 3 == a && 4 == a
                if (ev1.rhs.equals(ev2.rhs) && !ev1.lhs.equals(ev2.lhs)) {
                    return Action.FALSE;
                }
            }

            // EQ and NOT EQ
            if (value instanceof Negation ne && ne.expression instanceof Equals ev2) {
                // 3 == a && not (4 == a)  (the situation 3 == a && not (3 == a) has been solved as A && not A == False
                if (ev1.rhs.equals(ev2.rhs) && !ev1.lhs.equals(ev2.lhs)) {
                    return Action.SKIP;
                }
            }

            // GE and EQ (note: GE always comes after EQ)
            if (value instanceof GreaterThanZero ge) {
                GreaterThanZero.XB xb = ge.extract(evaluationContext);
                if (ev1.lhs instanceof Numeric ev1ln && ev1.rhs.equals(xb.x())) {
                    double y = ev1ln.doubleValue();
                    if (xb.lessThan()) {
                        // y==x and x <= b
                        if (ge.allowEquals() && y <= xb.b() || !ge.allowEquals() && y < xb.b()) {
                            return Action.SKIP;
                        }
                    } else {
                        // y==x and x >= b
                        if (ge.allowEquals() && y >= xb.b() || !ge.allowEquals() && y > xb.b()) {
                            return Action.SKIP;
                        }
                    }
                    return Action.FALSE;
                }
            }
            return Action.ADD;
        }

        //  GE and NOT EQ
        if (value instanceof GreaterThanZero ge && prev instanceof Negation prevNeg &&
                prevNeg.expression instanceof Equals equalsValue) {
            GreaterThanZero.XB xb = ge.extract(evaluationContext);
            if (equalsValue.lhs instanceof Numeric eqLn && equalsValue.rhs.equals(xb.x())) {
                double y = eqLn.doubleValue();

                // y != x && -b + x >= 0, in other words, x!=y && x >= b
                if (ge.allowEquals() && y < xb.b() || !ge.allowEquals() && y <= xb.b()) {
                    return Action.REPLACE;
                }
                // if b==y then the end result should be x>b
                if (y == xb.b() && ge.allowEquals()) {
                    newConcat.remove(newConcat.size() - 1);
                    newConcat.add(new GreaterThanZero(ge.getIdentifier(), ge.booleanParameterizedType(),
                            ge.expression(), false));
                    return Action.SKIP;
                }
            }
        }

        // GE and GE
        if (value instanceof GreaterThanZero ge2 && prev instanceof GreaterThanZero ge1) {
            GreaterThanZero.XB xb1 = ge1.extract(evaluationContext);
            GreaterThanZero.XB xb2 = ge2.extract(evaluationContext);
            if (xb1.x().equals(xb2.x())) {
                // x>= b1 && x >= b2, with < or > on either
                if (xb1.lessThan() && xb2.lessThan()) {
                    // x <= b1 && x <= b2
                    // (1) b1 > b2 -> keep b2
                    if (xb1.b() > xb2.b()) return Action.REPLACE;
                    // (2) b1 < b2 -> keep b1
                    if (xb1.b() < xb2.b()) return Action.SKIP;
                    if (ge1.allowEquals()) return Action.REPLACE;
                    return Action.SKIP;
                }
                if (!xb1.lessThan() && !xb2.lessThan()) {
                    // x >= b1 && x >= b2
                    // (1) b1 > b2 -> keep b1
                    if (xb1.b() > xb2.b()) return Action.SKIP;
                    // (2) b1 < b2 -> keep b2
                    if (xb1.b() < xb2.b()) return Action.REPLACE;
                    // (3) b1 == b2 -> > or >=
                    if (ge1.allowEquals()) return Action.REPLACE;
                    return Action.SKIP;
                }

                // !xb1.lessThan: x >= b1 && x <= b2; otherwise: x <= b1 && x >= b2
                if (xb1.b() > xb2.b()) return !xb1.lessThan() ? Action.FALSE : Action.ADD;
                if (xb1.b() < xb2.b()) return !xb1.lessThan() ? Action.ADD : Action.FALSE;
                if (ge1.allowEquals() && ge2.allowEquals()) {
                    Expression newValue = Equals.equals(evaluationContext,
                            IntConstant.intOrDouble(primitives, xb1.b()), xb1.x()); // null-checks are irrelevant here
                    newConcat.set(newConcat.size() - 1, newValue);
                    return Action.SKIP;
                }
                return Action.FALSE;
            }
            Expression notGe2 = Negation.negate(evaluationContext, ge2.expression());
            if (ge1.expression().equals(notGe2)) {
                if (ge1.allowEquals() && ge2.allowEquals()) {
                    // x >= 0, x <= 0 ==> x == 0
                    Expression result;
                    if (ge1.expression() instanceof Sum sum) {
                        result = sum.isZero(evaluationContext);
                    } else {
                        result = Equals.equals(evaluationContext, ge1.expression(), new IntConstant(evaluationContext.getPrimitives(), 0));
                    }
                    newConcat.set(newConcat.size() - 1, result);
                    return Action.SKIP;
                }
                return Action.FALSE;
            }
        }

        return Action.ADD;
    }

    private List<Expression> components(Expression value) {
        if (value instanceof Or or) {
            return or.expressions();
        }
        return List.of(value);
    }

    private static void recursivelyAdd(ArrayList<Expression> concat, List<Expression> values) {
        for (Expression value : values) {
            if (value instanceof And and) {
                recursivelyAdd(concat, and.expressions);
            } else {
                concat.add(value);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        And andValue = (And) o;
        return expressions.equals(andValue.expressions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expressions);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    public OutputBuilder output(Qualification qualification) {
        Precedence precedence = precedence();
        return new OutputBuilder()
                .add(expressions.stream().map(e -> outputInParenthesis(qualification, precedence, e))
                        .collect(OutputBuilder.joining(Symbol.LOGICAL_AND)));
    }

    @Override
    public ParameterizedType returnType() {
        return primitives.booleanParameterizedType();
    }

    @Override
    public Precedence precedence() {
        return Precedence.LOGICAL_AND;
    }

    /*
    this implementation gradually adds the clauses to the evaluation context, so that when we feed in

    a!=null && a.method()

    then a will not receive a CONTEXT_NOT_NULL==EFFECTIVELY_NOT_NULL, because it is not null in the context.
    For this to work, it is crucial that the clauses are presented in the correct order!
     */
    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo
            forwardEvaluationInfo) {
        List<EvaluationResult> clauseResults = new ArrayList<>(expressions.size());
        EvaluationContext context = evaluationContext;
        List<Expression> sortedExpressions = new ArrayList<>(expressions);
        Collections.sort(sortedExpressions);
        for (Expression expression : sortedExpressions) {
            EvaluationResult result = expression.evaluate(context, forwardEvaluationInfo);
            clauseResults.add(result);
            context = context.child(result.value());
        }
        Expression[] clauses = clauseResults.stream().map(EvaluationResult::value).toArray(Expression[]::new);
        return new EvaluationResult.Builder()
                .compose(clauseResults)
                .setExpression(And.and(evaluationContext, clauses))
                .build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_AND;
    }

    @Override
    public int internalCompareTo(Expression v) {
        if (v instanceof InlineConditional inlineConditional) {
            return internalCompareTo(inlineConditional.condition);
        }
        And andValue = (And) v;
        return ListUtil.compare(expressions, andValue.expressions);
    }

    @Override
    public List<Variable> variables() {
        return expressions.stream().flatMap(v -> v.variables().stream()).collect(Collectors.toList());
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext
                                               evaluationContext, Map<Expression, Expression> translation) {
        List<EvaluationResult> reClauseERs = expressions.stream()
                .map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        Expression[] reClauses = reClauseERs.stream().map(EvaluationResult::value).toArray(Expression[]::new);
        return new EvaluationResult.Builder()
                .compose(reClauseERs)
                .setExpression(And.and(evaluationContext, reClauses))
                .build();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expressions.forEach(v -> v.visit(predicate));
        }
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property,
                          boolean duringEvaluation) {
        return UnknownExpression.primitiveGetProperty(property);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        if (translationMap.isEmpty()) return this;
        List<Expression> translated = expressions.stream().map(e -> e.translate(translationMap)).toList();
        return new And(identifier, primitives, translated);
    }

    @Override
    public List<? extends Element> subElements() {
        return expressions;
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    public List<Expression> getExpressions() {
        return expressions;
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return expressions.stream().map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }
}
