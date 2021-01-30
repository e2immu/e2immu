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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.Element;
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

public record And(Primitives primitives,
                  List<Expression> expressions,
                  ObjectFlow objectFlow) implements Expression {

    public And {
        Objects.requireNonNull(primitives);
        Objects.requireNonNull(expressions);
        Objects.requireNonNull(objectFlow);
    }

    // testing only
    public And(Primitives primitives) {
        this(primitives, List.of(), ObjectFlow.NO_FLOW);
    }

    public And(Primitives primitives, ObjectFlow objectFlow) {
        this(primitives, List.of(), objectFlow);
    }

    private enum Action {
        SKIP, REPLACE, FALSE, TRUE, ADD, ADD_CHANGE
    }

    // we try to maintain a CNF
    public Expression append(EvaluationContext evaluationContext, Expression... values) {

        // STEP 1: check that all values return boolean!

        if (Arrays.stream(values).anyMatch(v -> v.isUnknown() || v.returnType() == null || Primitives.isNotBooleanOrBoxedBoolean(v.returnType()))) {
            throw new UnsupportedOperationException("Internal error, values are " + Arrays.toString(values));
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
                    log(CNF, "Return FALSE in And, found FALSE", value);
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
            log(CNF, "And reduced to 0 components, return true");
            return new BooleanConstant(primitives, true);
        }
        if (concat.size() == 1) {
            log(CNF, "And reduced to 1 component: {}", concat.get(0));
            return concat.get(0);
        }
        And res = new And(primitives, ImmutableList.copyOf(concat), objectFlow);
        log(CNF, "Constructed {}", res);
        return res;
    }

    private Action analyse(EvaluationContext evaluationContext, int pos, ArrayList<Expression> newConcat,
                           Expression prev, Expression value) {
        // A && A
        if (value.equals(prev)) return Action.SKIP;

        // this works because of sorting
        // A && !A will always sit next to each other
        if (value instanceof Negation negatedValue && negatedValue.expression.equals(prev)) {
            log(CNF, "Return FALSE in And, found opposites for {}", value);
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
                    log(CNF, "Return FALSE in And, found opposite for {}", value);
                    return Action.FALSE;
                }
                // replace
                Expression orValue = new Or(primitives, objectFlow)
                        .append(evaluationContext, remaining);
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
                Expression orValue = new Or(primitives, objectFlow).append(evaluationContext, equal);
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

        // combinations with equality

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
                            IntConstant.intOrDouble(primitives, xb1.b(), ge1.getObjectFlow()),
                            xb1.x(), ge1.getObjectFlow()); // null-checks are irrelevant here
                    newConcat.set(newConcat.size() - 1, newValue);
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

    @Override
    public boolean hasBeenEvaluated() {
        assert objectFlow != ObjectFlow.NYE;
        return true;
    }

    public OutputBuilder output() {
        Precedence precedence = precedence();
        return new OutputBuilder()
                .add(expressions.stream().map(e -> e.outputInParenthesis(precedence, e))
                        .collect(OutputBuilder.joining(Symbol.LOGICAL_AND)));
    }

    @Override
    public ParameterizedType returnType() {
        return primitives.booleanParameterizedType;
    }

    @Override
    public Precedence precedence() {
        return Precedence.LOGICAL_AND;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return null;
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
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public List<Variable> variables() {
        return expressions.stream().flatMap(v -> v.variables().stream()).collect(Collectors.toList());
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        List<EvaluationResult> reClauseERs = expressions.stream()
                .map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        Expression[] reClauses = reClauseERs.stream().map(EvaluationResult::value).toArray(Expression[]::new);
        return new EvaluationResult.Builder()
                .compose(reClauseERs)
                .setExpression(new And(primitives, objectFlow).append(evaluationContext, reClauses))
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

    public record CommonComponentResult(Expression common, Expression rest1, Expression rest2) {
    }

    private Expression toAnd(EvaluationContext evaluationContext, Collection<Expression> list) {
        if (list.isEmpty()) return new BooleanConstant(primitives, true);
        return new And(primitives, objectFlow).append(evaluationContext, list.toArray(Expression[]::new));
    }

    public CommonComponentResult findCommon(EvaluationContext evaluationContext, Expression other) {
        if (other instanceof And otherAnd) {
            List<Expression> common = new ArrayList<>(Math.min(expressions.size(), otherAnd.expressions.size()));
            List<Expression> rest = new ArrayList<>(expressions.size());
            Set<Expression> otherRest = new HashSet<>(otherAnd.expressions); // make the copy
            for (Expression expression : expressions) {
                if (otherRest.contains(expression)) {
                    common.add(expression);
                    otherRest.remove(expression);
                } else {
                    rest.add(expression);
                }
            }
            return new CommonComponentResult(toAnd(evaluationContext, common), toAnd(evaluationContext, rest),
                    toAnd(evaluationContext, otherRest));
        }
        if (expressions.contains(other)) {
            return new CommonComponentResult(other, new And(primitives, objectFlow).append(evaluationContext,
                    expressions.stream().filter(e -> !e.equals(other)).toArray(Expression[]::new)), new BooleanConstant(primitives, true));
        }
        return new CommonComponentResult(new BooleanConstant(primitives, true), this, other);
    }

    @Override
    public List<? extends Element> subElements() {
        return expressions;
    }
}
