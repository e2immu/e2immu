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
import org.e2immu.analyser.model.expression.util.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.IntUtil;
import org.e2immu.analyser.util.ListUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class And extends ExpressionCanBeTooComplex {
    private static final Logger LOGGER = LoggerFactory.getLogger(And.class);

    private final Primitives primitives;
    private final List<Expression> expressions;

    public And(Primitives primitives, List<Expression> expressions) {
        this(Identifier.joined("and", expressions.stream().map(Expression::getIdentifier).toList()),
                primitives, expressions);
    }

    private And(Identifier identifier, Primitives primitives, List<Expression> expressions) {
        super(identifier, 1 + expressions.stream().mapToInt(Expression::getComplexity).sum());
        this.primitives = Objects.requireNonNull(primitives);
        this.expressions = Objects.requireNonNull(expressions);
    }

    private And(Identifier identifier, Primitives primitives) {
        this(identifier, primitives, List.of());
    }

    private enum Action {
        SKIP, REPLACE, FALSE, TRUE, ADD, ADD_CHANGE
    }

    public static Expression and(EvaluationResult context, Expression... values) {
        Identifier id = Identifier.joined("and", Arrays.stream(values).map(Expression::getIdentifier).toList());
        return and(id, context, values);
    }

    /**
     * @param context                  the context to create new evaluated expression
     * @param allowEqualsToCallContext a boolean to prevent a stackoverflow, repeatedly trying to determine whether something is not null or not. (See e.g. Store_0.)
     * @param values                   the clauses of the and
     * @return the result of the conjunction
     */
    public static Expression and(EvaluationResult context, boolean allowEqualsToCallContext, Expression... values) {
        Identifier id = Identifier.joined("and", Arrays.stream(values).map(Expression::getIdentifier).toList());
        return and(id, context, allowEqualsToCallContext, values);
    }

    public static Expression and(Identifier identifier, EvaluationResult context, Expression... values) {
        return and(identifier, context, false, values);
    }

    public static Expression and(Identifier identifier, EvaluationResult context, boolean allowEqualsToCallContext, Expression... values) {
        Expression expression = new And(identifier, context.getPrimitives()).append(context, allowEqualsToCallContext, values);
        if (expression.isDone()) {
            CausesOfDelay causes = Arrays.stream(values).map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
            if (causes.isDelayed()) {
                return DelayedExpression.forSimplification(identifier,
                        context.getPrimitives().booleanParameterizedType(), expression, causes);
            }
        }
        return expression;
    }

    // we try to maintain a CNF
    private Expression append(EvaluationResult context, boolean allowEqualsToCallContext, Expression... values) {
        assert !allowEqualsToCallContext || identifier == Identifier.CONSTANT;

        // STEP 1: check that all values return boolean!
        int complexity = 0;
        for (Expression v : values) {
            assert !v.isEmpty() : "Unknown value " + v + " in And";
            assert v.returnType() != null : "Null return type for " + v + " in And";
            assert v.returnType().isBooleanOrBoxedBoolean() || v.returnType().isUnboundTypeParameter()
                    : "Non-boolean return type for " + v + " in And: " + v.returnType();

            complexity += v.getComplexity();
        }

        // STEP 2: trivial reductions

        if (this.expressions.isEmpty() && values.length == 1 && values[0] instanceof And) return values[0];

        // STEP 3: concat everything

        ArrayList<Expression> concat = new ArrayList<>(values.length + this.expressions.size());
        concat.addAll(this.expressions);
        recursivelyAdd(concat, Arrays.stream(values).collect(Collectors.toList()));

        // STEP 4: loop

        boolean changes = complexity < context.evaluationContext().limitOnComplexity();
        if (!changes) {
            LOGGER.debug("Not analysing AND operation, complexity {}", complexity);
            return reducedComplexity(identifier, context, expressions, values);
        }
        assert complexity < Expression.HARD_LIMIT_ON_COMPLEXITY : "Complexity reached " + complexity;

        while (changes) {
            changes = false;

            // STEP 4a: sort

            concat = AndOrSorter.sort(context, concat);

            // STEP 4b: observations

            for (Expression value : concat) {
                if (value instanceof BooleanConstant bc && !bc.constant()) {
                    LOGGER.debug("Return FALSE in And, found FALSE");
                    return new BooleanConstant(primitives, false);
                }
            }
            concat.removeIf(value -> value instanceof BooleanConstant); // TRUE can go

            // STEP 4c: reductions

            ArrayList<Expression> newConcat = new ArrayList<>(concat.size());
            Expression prev = null;
            int pos = 0;
            for (Expression value : concat) {

                Action action = analyse(context, allowEqualsToCallContext, pos, newConcat, prev, value);
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
            LOGGER.debug("And reduced to 0 components, return true");
            return new BooleanConstant(primitives, true);
        }
        if (concat.size() == 1) {
            LOGGER.debug("And reduced to 1 component: {}", concat.get(0));
            return concat.get(0);
        }
        Identifier id = Identifier.joined("and", concat.stream().map(Expression::getIdentifier).toList());
        And res = new And(id, primitives, List.copyOf(concat));
        LOGGER.debug("Constructed {}", res);
        return res;
    }

    private Action analyse(EvaluationResult evaluationContext,
                           boolean allowEqualsToCallContext,
                           int pos, ArrayList<Expression> newConcat,
                           Expression prev, Expression value) {
        // A && A
        if (value.equals(prev)) return Action.SKIP;

        // this works because of sorting
        // A && !A will always sit next to each other
        if (value instanceof Negation negatedValue && negatedValue.expression.equals(prev)) {
            LOGGER.debug("Return FALSE in And, found direct opposite for {}", value);
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
            newConcat.set(newConcat.size() - 1, conditionalValue.ifFalse);
            return Action.ADD;
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
                Expression negated1 = Negation.negate(evaluationContext, allowEqualsToCallContext, value1);
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
                    LOGGER.debug("Return FALSE in And, found opposite for {}", value);
                    return Action.FALSE;
                }
                // replace
                Expression orValue = Or.or(evaluationContext, remaining);
                LOGGER.debug("Replace {} by {}, found opposite in {}", value, orValue, newConcat);
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
                        LOGGER.debug("Skipping {} in OR, already in other clause of And", value);
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
                LOGGER.debug("Skipping {} in OR, simplified to {}", value, orValue);
                return Action.SKIP;
            }
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

        // x.equals(y)
        Action actionEqualsEquals = analyseEqualsEquals(evaluationContext, allowEqualsToCallContext, prev, value, newConcat);
        if (actionEqualsEquals != null) return actionEqualsEquals;

        // x == y
        Action actionEqEq = analyseEqEq(evaluationContext, allowEqualsToCallContext, prev, value, newConcat);
        if (actionEqEq != null) return actionEqEq;

        Action actionGeNotEqual = analyseGeNotEq(evaluationContext, newConcat, prev, value);
        if (actionGeNotEqual != null) return actionGeNotEqual;

        Action actionGeGe = analyseGeGe(evaluationContext, newConcat, prev, value);
        if (actionGeGe != null) return actionGeGe;

        Action actionInstanceOf = analyseInstanceOf(evaluationContext, prev, value);
        if (actionInstanceOf != null) return actionInstanceOf;


        // simplification of the OrValue

        if (value instanceof Or orValue) {
            if (orValue.expressions().size() == 1) {
                newConcat.add(orValue.expressions().get(0));
                LOGGER.debug("Simplification of OR into single And clause: {}", value);
                return Action.SKIP;
            }
        }

        return Action.ADD;
    }

    private Action analyseEqualsEquals(EvaluationResult evaluationContext,
                                       boolean allowEqualsToCallContext,
                                       Expression prev,
                                       Expression value,
                                       ArrayList<Expression> newConcat) {
        LhsRhs ev1 = LhsRhs.equalsMethodCall(prev);
        if (ev1 != null && ev1.lhs().isConstant()) {
            Action a = equalsRhs(ev1, value);
            if (a != null) return a;

            return equalsAndOr(evaluationContext, allowEqualsToCallContext, prev, value, newConcat, ev1.rhs());
        }
        return null;
    }

    private Action equalsRhs(LhsRhs ev1, Expression value) {
        LhsRhs ev2 = LhsRhs.equalsMethodCall(value);
        if (ev2 != null && ev2.lhs().isConstant()) {
            // "a".equals(s) && "b".equals(s)
            if (ev1.rhs().equals(ev2.rhs()) && !ev1.lhs().equals(ev2.lhs())) {
                return Action.FALSE;
            }
        }

        // EQ and NOT EQ
        LhsRhs ev2b;
        if (value instanceof Negation ne && ((ev2b = LhsRhs.equalsMethodCall(ne.expression)) != null)) {
            // "a".equals(s) && !"b".equals(s)
            if (ev1.rhs().equals(ev2b.rhs()) && !ev1.lhs().equals(ev2b.lhs())) {
                return Action.SKIP;
            }
        }
        return null;
    }

    private Action analyseEqEq(EvaluationResult evaluationContext,
                               boolean allowEqualsToCallContext,
                               Expression prev,
                               Expression value,
                               ArrayList<Expression> newConcat) {
        if (prev instanceof Equals ev1) {
            Action skip = equalsAndOr(evaluationContext, allowEqualsToCallContext, prev, value, newConcat, ev1.rhs);
            if (skip != null) return skip;
            if (value instanceof Equals ev2) {
                // 3 == a && 4 == a
                if (ev1.rhs.equals(ev2.rhs) && !ev1.lhs.equals(ev2.lhs)) {
                    return Action.FALSE;
                }
                // x == a%r && y == a
                if (ev1.rhs instanceof Remainder remainder && ev2.rhs.equals(remainder.lhs)) {
                    // let's evaluate x == y%r; if true, we can skip; if false, we can bail out
                    Expression yModR = Remainder.remainder(evaluationContext, ev2.lhs, remainder.rhs);
                    if (yModR.isNumeric() && ev1.lhs.isNumeric()) {
                        if (yModR.equals(ev1.lhs)) {
                            return Action.REPLACE;
                        }
                        return Action.FALSE;
                    }
                    // this is a very limited implementation!!
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
        }
        return null;
    }

    private Action equalsAndOr(EvaluationResult evaluationContext,
                               boolean allowEqualsToCallContext,
                               Expression prev,
                               Expression value,
                               ArrayList<Expression> newConcat,
                               Expression equalityRhs) {
        if (value instanceof Or or) {
            // do a check first -- should we expand?
            if (safeToExpandOr(equalityRhs, or)) {
                List<Expression> result = new ArrayList<>(or.expressions().size());
                boolean foundTrue = false;
                for (Expression clause : or.expressions()) {
                    Identifier id = allowEqualsToCallContext ? Identifier.CONSTANT
                            : Identifier.joined("and", List.of(prev.getIdentifier(), clause.getIdentifier()));
                    Expression and = new And(id, evaluationContext.getPrimitives())
                            .append(evaluationContext, allowEqualsToCallContext, prev, clause);
                    if (and.isBoolValueTrue()) {
                        foundTrue = true;
                        break;
                    }
                    if (!and.isBoolValueFalse()) {
                        result.add(and);
                    }
                }
                if (foundTrue) {
                    return Action.SKIP;
                }
                if (result.isEmpty()) {
                    return Action.FALSE;
                }
                if (result.size() < or.expressions().size()) {
                    Expression newOr = Or.or(evaluationContext, result);
                    newConcat.set(newConcat.size() - 1, newOr); // full replace
                    return Action.ADD_CHANGE;
                }
            }
        }
        return null;
    }

    // starting off with "x == a", we're looking for comparisons to "a", and equality with "a"
    public static boolean safeToExpandOr(Expression rhs, Or or) {
        return or.expressions().stream().allMatch(clause -> extract(clause).equals(rhs));
    }

    public static Expression extract(Expression e) {
        if (e instanceof Equals equals) return equals.rhs;
        if (e instanceof GreaterThanZero gt0) {
            return extract(gt0.expression());
        }
        if (e instanceof Negation negation) return extract(negation.expression);
        if (e instanceof Sum sum && sum.lhs.isConstant()) return extract(sum.rhs);
        LhsRhs lhsRhs = LhsRhs.equalsMethodCall(e);
        if (lhsRhs != null) return lhsRhs.rhs();
        return e;
    }


    private Action analyseGeNotEq(EvaluationResult evaluationContext, ArrayList<Expression> newConcat, Expression prev, Expression value) {
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
                    GreaterThanZero gt;
                    if (ge.expression().returnType().equals(primitives.intParameterizedType())) {
                        Expression oneLess = Sum.sum(evaluationContext, ge.expression(),
                                IntConstant.minusOne(primitives));
                        gt = new GreaterThanZero(ge.getIdentifier(), primitives, oneLess, true);
                    } else {
                        gt = new GreaterThanZero(ge.getIdentifier(), primitives, ge.expression(), false);
                    }
                    newConcat.add(gt);
                    return Action.SKIP;
                }
            }
        }
        return null;
    }

    private Action analyseGeGe(EvaluationResult evaluationContext, ArrayList<Expression> newConcat, Expression prev, Expression value) {
        // GE and GE
        if (value instanceof GreaterThanZero ge2 && prev instanceof GreaterThanZero ge1) {
            GreaterThanZero.XB xb1 = ge1.extract(evaluationContext);
            GreaterThanZero.XB xb2 = ge2.extract(evaluationContext);
            Expression notXb2x = Negation.negate(evaluationContext, xb2.x());
            Boolean reverse = xb1.x().equals(xb2.x()) ? Boolean.FALSE : xb1.x().equals(notXb2x) ? Boolean.TRUE : null;
            if (reverse != null) {
                Expression xb1x = xb1.x();
                double xb1b = xb1.b();
                double xb2b = reverse ? -xb2.b() : xb2.b();
                boolean xb1lt = xb1.lessThan();
                boolean xb2lt = reverse != xb2.lessThan();

                // x>= b1 && x >= b2, with < or > on either
                if (xb1lt && xb2lt) {
                    // x <= b1 && x <= b2
                    // (1) b1 > b2 -> keep b2
                    if (xb1b > xb2b) return Action.REPLACE;
                    // (2) b1 < b2 -> keep b1
                    if (xb1b < xb2b) return Action.SKIP;
                    if (ge1.allowEquals()) return Action.REPLACE;
                    return Action.SKIP;
                }
                if (!xb1lt && !xb2lt) {
                    // x >= b1 && x >= b2
                    // (1) b1 > b2 -> keep b1
                    if (xb1b > xb2b) return Action.SKIP;
                    // (2) b1 < b2 -> keep b2
                    if (xb1b < xb2b) return Action.REPLACE;
                    // (3) b1 == b2 -> > or >=
                    if (ge1.allowEquals()) return Action.REPLACE;
                    return Action.SKIP;
                }

                // !xb1.lessThan: x >= b1 && x <= b2; otherwise: x <= b1 && x >= b2
                if (xb1b > xb2b) return !xb1lt ? Action.FALSE : Action.ADD;
                if (xb1b < xb2b) return !xb1lt ? Action.ADD : Action.FALSE;
                if (IntUtil.isMathematicalInteger(xb1b)) {
                    Expression newValue = Equals.equals(evaluationContext,
                            IntConstant.intOrDouble(primitives, identifier, xb1b), xb1x); // null-checks are irrelevant here
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
                        result = Equals.equals(evaluationContext, ge1.expression(),
                                IntConstant.zero(evaluationContext.getPrimitives()));
                    }
                    newConcat.set(newConcat.size() - 1, result);
                    return Action.SKIP;
                }
                return Action.FALSE;
            }
        }
        return null;
    }

    private Action analyseInstanceOf(EvaluationResult evaluationContext, Expression prev, Expression value) {
        // a instanceof A && a instanceof B
        if (value instanceof InstanceOf i1 && prev instanceof InstanceOf i2 && i1.expression().equals(i2.expression())) {
            if (i1.parameterizedType().isAssignableFrom(evaluationContext.getAnalyserContext(), i2.parameterizedType())) {
                // i1 is the most generic, so skip
                return Action.SKIP;
            }
            if (i2.parameterizedType().isAssignableFrom(evaluationContext.getAnalyserContext(), i1.parameterizedType())) {
                // i2 is the most generic, so keep current
                return Action.REPLACE;
            }
            return Action.FALSE;
        }

        // a instanceof A && !(a instanceof B)
        // is written as: a instanceof A && (null==a||!(a instanceof B))
        InstanceOf negI1 = isNegationOfInstanceOf(value);
        if (negI1 != null && prev instanceof InstanceOf i2 && negI1.expression().equals(i2.expression())) {
            if (negI1.parameterizedType().isAssignableFrom(evaluationContext.getAnalyserContext(), i2.parameterizedType())) {
                // B is the most generic, so we have a contradiction
                return Action.FALSE;
            }
            if (i2.parameterizedType().isAssignableFrom(evaluationContext.getAnalyserContext(), negI1.parameterizedType())) {
                // i1 is the most generic, i2 is more specific; we keep what we have
                return Action.ADD;
            }
            // A unrelated to B, we drop the negation
            return Action.SKIP;
        }

        // !(a instanceof A) && a instanceof B
        InstanceOf negI2 = isNegationOfInstanceOf(prev);
        if (value instanceof InstanceOf i1 && negI2 != null && negI2.expression().equals(i1.expression())) {
            if (negI2.parameterizedType().isAssignableFrom(evaluationContext.getAnalyserContext(), i1.parameterizedType())) {
                // B is the most generic, so we have a contradiction
                return Action.FALSE;
            }
            if (i1.parameterizedType().isAssignableFrom(evaluationContext.getAnalyserContext(), negI2.parameterizedType())) {
                // i1 is the most generic, i2 is more specific; we keep what we have
                return Action.ADD;
            }
            // A unrelated to B, we drop the negation
            return Action.REPLACE;
        }

        // null != a && a instanceof B
        if (value instanceof InstanceOf i
                && i.expression() instanceof VariableExpression iv
                && prev instanceof Negation neg
                && neg.expression instanceof Equals eq
                && eq.lhs.isNullConstant()
                && eq.rhs instanceof VariableExpression ve && ve.variable().equals(iv.variable())) {
            // remove previous
            return Action.REPLACE;
        }
        // null == a && a instanceof B
        if (value instanceof InstanceOf i
                && i.expression() instanceof VariableExpression iv
                && prev instanceof Equals eq
                && eq.lhs.isNullConstant()
                && eq.rhs instanceof VariableExpression ve && ve.variable().equals(iv.variable())) {
            // remove previous
            return Action.FALSE;
        }
        return null;
    }

    // a instanceof A && !(a instanceof B)
    // is written as: a instanceof A && (null==a||!(a instanceof B))
    private InstanceOf isNegationOfInstanceOf(Expression expression) {
        return expression instanceof Or or
                && or.expressions().size() == 2
                && or.expressions().get(0) instanceof Equals equals
                && equals.lhs.isNullConstant()
                && or.expressions().get(1) instanceof Negation negation
                && negation.expression instanceof InstanceOf instance
                && instance.expression().equals(equals.rhs) ? instance : null;
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
    public EvaluationResult evaluate(EvaluationResult evaluationResult, ForwardEvaluationInfo
            forwardEvaluationInfo) {
        List<EvaluationResult> clauseResults = new ArrayList<>(expressions.size());
        EvaluationResult context = evaluationResult;
        List<Expression> sortedExpressions = AndOrSorter.sort(evaluationResult, expressions);
        Set<Variable> conditionVariables = new HashSet<>();
        for (Expression expression : sortedExpressions) {
            EvaluationResult result = expression.evaluate(context, forwardEvaluationInfo);
            conditionVariables.addAll(expression.variables());
            conditionVariables.addAll(result.value().variables());
            clauseResults.add(result);
            context = context.child(result.value(), Set.copyOf(conditionVariables));
        }
        Expression[] clauses = clauseResults.stream().map(EvaluationResult::value).toArray(Expression[]::new);
        return new EvaluationResultImpl.Builder(context)
                .compose(clauseResults)
                .setExpression(And.and(evaluationResult, clauses))
                .build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_AND;
    }

    @Override
    public int internalCompareTo(Expression v) {
        And andValue = (And) v;
        return ListUtil.compare(expressions, andValue.expressions);
    }

    @Override
    public List<Variable> variables(DescendMode descendIntoFieldReferences) {
        return expressions.stream().flatMap(v -> v.variables(descendIntoFieldReferences).stream())
                .collect(Collectors.toList());
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            expressions.forEach(v -> v.visit(predicate));
        }
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return getPropertyForPrimitiveResults(property);
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;
        List<Expression> translatedExpressions = expressions.isEmpty() ? expressions : expressions.stream()
                .map(e -> e.translate(inspectionProvider, translationMap))
                .collect(TranslationCollectors.toList(expressions));
        if (expressions == translatedExpressions) return this;
        return new And(identifier, primitives, translatedExpressions);
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

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        return new And(identifier, primitives, expressions.stream()
                .map(e -> e.isDelayed() ? e.mergeDelays(causesOfDelay) : e)
                .toList());
    }

    public Expression removePartsNotReferringTo(EvaluationResult evaluationContext, Variable variable) {
        Expression[] filtered = this.expressions.stream()
                .filter(e -> e.variables().contains(variable))
                .toArray(Expression[]::new);
        return And.and(evaluationContext, filtered);
    }

    @Override
    public Expression removeAllReturnValueParts(Primitives primitives) {
        List<Expression> filtered = this.expressions.stream()
                .map(e -> e.removeAllReturnValueParts(primitives))
                .filter(Objects::nonNull)
                .toList();
        if (filtered.size() == 1) return filtered.get(0);
        if (filtered.isEmpty()) return new BooleanConstant(primitives, true);
        return new And(primitives, filtered);
    }
}
