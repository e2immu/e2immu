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

package org.e2immu.analyser.parser.own.snippet.testexample;


import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// trying to catch an error
public class Expressions_0 {

    interface Expression {
        default boolean isInstanceOf(Class<? extends Expression> clazz) {
            return clazz.isAssignableFrom(getClass());
        }

        default <T extends Expression> T asInstanceOf(Class<T> clazz) {
            if (clazz.isAssignableFrom(getClass())) {
                return (T) this;
            }
            return null;
        }
    }

    record And(List<Expression> expressions) implements Expression {

    }

    record GreaterThanZero(Expression expression, boolean allowEquals) implements Expression {

    }

    interface Inequality {

    }

    interface OneVariable {
        Variable variable();
    }

    interface Variable extends OneVariable {

    }

    public Boolean evaluate(Expression expression) {
        if (expression instanceof And and) {
            return and.expressions().stream().map(this::accept1)
                    .reduce(true, (v1, v2) -> v1 == null ? v2 : v2 == null ? v1 : v1 && v2);
        }
        return accept1(expression);
    }

    private final Map<OneVariable, List<Expression>> perComponent = new HashMap<>();

    private Boolean accept1(Expression expression) {
        if (expression instanceof GreaterThanZero gt0) {
            Inequality inequality = extract(gt0);

            if (inequality instanceof LinearInequalityInOneVariable oneVar) {
                List<Expression> expressionsInV = perComponent.getOrDefault(oneVar.v(), List.of());
                if (expressionsInV.isEmpty()) return null;
                return oneVar.accept3(expressionsInV);
            }

            if (inequality instanceof LinearInequalityInTwoVariables twoVars) {
                List<Expression> expressionsInX = perComponent.getOrDefault(twoVars.x(), List.of());
                List<Expression> expressionsInY = perComponent.getOrDefault(twoVars.y(), List.of());
                if (expressionsInX.isEmpty() || expressionsInY.isEmpty()) return null;

                return twoVars.accept6(expressionsInX, expressionsInY);
            }
        }
        return null;
    }


    private record Term(double a, OneVariable v) {
    }

    public static Inequality extract(GreaterThanZero gt0) {
        List<Term> terms = new ArrayList<>();
        if (!recursivelyCollectTerms(gt0.expression(), terms)) return null;
        List<Term> withoutVariable = terms.stream().filter(t -> t.v == null).toList();
        if (withoutVariable.size() > 1) return null;
        double c = withoutVariable.isEmpty() ? 0.0 : withoutVariable.get(0).a;

        List<Term> withVariable = terms.stream().filter(t -> t.v != null).toList();
        if (withVariable.size() == 1) {
            Term t1 = withVariable.get(0);
            return new LinearInequalityInOneVariable(t1.a, t1.v, c, gt0.allowEquals());
        }
        if (withVariable.size() == 2) {
            Term t1 = withVariable.get(0);
            Term t2 = withVariable.get(1);
            return new LinearInequalityInTwoVariables(t1.a, t1.v, t2.a, t2.v, c, gt0.allowEquals());
        }
        // not recognized
        return null;
    }

    record Sum(Expression lhs, Expression rhs) implements Expression {

    }

    record Product(Expression lhs, Expression rhs) implements Expression {

    }

    record Equals(Expression lhs, Expression rhs) implements Expression {

    }

    record Negation(Expression expression) implements Expression {

    }

    static class ConstantExpression<T> implements Expression {
        private final T t;

        public ConstantExpression(T t) {
            this.t = t;
        }

        T getValue() {
            return t;
        }
    }

    // important: leave the @NotNull contract here for testing; causes/d error
    private static boolean recursivelyCollectTerms(@NotNull(contract = true) Expression expression, List<Term> terms) {
        if (expression instanceof Sum sum) {
            if (!recursivelyCollectTerms(sum.lhs, terms)) return false;
            return recursivelyCollectTerms(sum.rhs, terms);
        }
        OneVariable oneVariableRhs;
        if (expression instanceof Product product &&
                product.lhs instanceof ConstantExpression<?> ce
                && ((oneVariableRhs = extractOneVariable(product.rhs)) != null)) {
            terms.add(new Term(extractDouble((Number) ce.getValue()), oneVariableRhs));
            return true;
        }
        OneVariable oneVariable;
        if ((oneVariable = extractOneVariable(expression)) != null) {
            terms.add(new Term(1.0, oneVariable));
            return true;
        }
        if (expression instanceof ConstantExpression<?> ce) {
            terms.add(new Term(extractDouble((Number) ce.getValue()), null));
            return true;
        }
        if (expression instanceof Negation negation) {
            List<Term> sub = new ArrayList<>();
            if (!recursivelyCollectTerms(negation.expression, sub)) return false;
            sub.forEach(term -> terms.add(new Term(-term.a, term.v)));
            return true;
        }
        return false;
    }

    // code has been simplified to detect bug more easily
    private static OneVariable extractOneVariable(Expression expression) {
        VariableExpression ve;
      //  if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) return ve.variable();
        if (expression instanceof MethodCall mc) {
            return mc;
        }
        return null;
    }

    record MethodCall(Expression object, Variable variable) implements Expression, OneVariable {
    }

    record LinearInequalityInOneVariable(double a,
                                         OneVariable v,
                                         double b,
                                         boolean allowEquals) implements Inequality {

        public LinearInequalityInOneVariable {
            assert a != 0.0;
            assert v != null;
        }

        public boolean accept2(double x) {
            double sum = a * x + b;
            return allowEquals ? sum >= 0 : sum > 0;
        }

        /*
        av + b >= 0 <=> v >= -b/a (with a>0.0) or v <= b/a (a<0.0)
         */
        public Interval interval() {
            double bOverA = b / a;
            if (a > 0.0) {
                return new Interval(-bOverA, allowEquals, Double.POSITIVE_INFINITY, true);
            }
            return new Interval(Double.NEGATIVE_INFINITY, true, bOverA, allowEquals);
        }

        /*
        null = not applicable; true = compatible/there are solutions; false = incompatible/no solutions
         */
        public Boolean accept3(List<Expression> expressionsInV) {
            if (onlyNotEquals(expressionsInV)) return true; // v != some constant
            Double vEquals = extractEquals(expressionsInV); // v == some constant
            if (vEquals != null) return accept2(vEquals);
            Interval i = Interval.extractInterval1(expressionsInV);
            if (i != null) return accept4(i);
            return null;
        }

        public Boolean accept4(Interval i) {
            if (i.isPoint()) return accept2(i.left());
            if (a > 0.0) {
                if (i.isOpenRight()) return true;
                assert i.isOpenLeft() || i.isClosed();
                // v >= -b/a; x<=right (or left <= x <= right); solution when -b/a <= right
                double diff = -b / a - i.right();
                return allowEquals && i.rightIncluded() ? diff <= 0 : diff < 0;
            }
            if (i.isOpenLeft()) return true;
            assert i.isOpenRight() || i.isClosed();
            // v <= -b/a; left <= x (or left <= x <= right); solution when left <= -b/a
            double diff = -b / a - i.left();
            return allowEquals && i.leftIncluded() ? diff >= 0 : diff > 0;
        }
    }

    record Interval(double left, boolean leftIncluded, double right, boolean rightIncluded) {

        public Interval {
            assert Double.isFinite(left) || Double.isFinite(right);
            assert left < right || left == right && (leftIncluded || rightIncluded);
            assert Double.isFinite(left) || left == Double.NEGATIVE_INFINITY;
            assert Double.isFinite(right) || right == Double.POSITIVE_INFINITY;
        }

        public boolean isPoint() {
            return left == right;
        }

        public boolean isOpenLeft() {
            return left == Double.NEGATIVE_INFINITY && Double.isFinite(right);
        }

        public boolean isOpenRight() {
            return right == Double.POSITIVE_INFINITY && Double.isFinite(left);
        }

        public boolean isClosed() {
            return Double.isFinite(left) && Double.isFinite(right);
        }

        public Interval combine(Interval other) {
            if (isOpenLeft()) {
                assert other.isOpenRight();
                return new Interval(other.left, other.leftIncluded, right, other.rightIncluded);
            }
            if (isOpenRight()) {
                assert other.isOpenLeft();
                return new Interval(left, leftIncluded, other.right, other.rightIncluded);
            }
            throw new IllegalStateException();
        }

        public static Interval extractInterval1(List<Expression> expressions) {
            if (expressions.size() == 1) {
                return extractInterval2(expressions.get(0));
            }
            if (expressions.size() == 2) {
                Interval i1 = extractInterval2(expressions.get(0));
                Interval i2 = extractInterval2(expressions.get(1));
                return i1 == null || i2 == null ? null : i1.combine(i2);
            }
            return null;
        }

        public static Interval extractInterval2(Expression expression) {
            if (expression instanceof GreaterThanZero ge) {
                Inequality inequality = extract(ge);
                if (inequality instanceof LinearInequalityInOneVariable one) return one.interval();
            }
            return null;
        }
    }

    record LinearInequalityInTwoVariables(double a,
                                          OneVariable x,
                                          double b,
                                          OneVariable y,
                                          double c, boolean allowEquals) implements Inequality {
        public LinearInequalityInTwoVariables {
            assert a != 0.0;
            assert b != 0.0;
            assert x != null;
            assert y != null;
            assert !x.equals(y);
        }

        public boolean accept5(double px, double py) {
            double sum = a * px + b * py + c;
            return allowEquals ? sum >= 0 : sum > 0;
        }

        public boolean isOpenLeftX() {
            return a < 0;
        }

        public boolean isOpenRightX() {
            return a > 0;
        }

        public boolean isOpenLeftY() {
            return b < 0;
        }

        public boolean isOpenRightY() {
            return b > 0;
        }

        public Boolean accept6(List<Expression> expressionsInX, List<Expression> expressionsInY) {
            if (onlyNotEquals(expressionsInX) || onlyNotEquals(expressionsInY)) return true;
            Double xEquals = extractEquals(expressionsInX);
            Double yEquals = extractEquals(expressionsInY);
            if (xEquals != null && yEquals != null) {
                return accept5(xEquals, yEquals);
            }
            if (xEquals != null) {
                // we have x to a constant, and inequalities for y => linear inequality in one variable
                LinearInequalityInOneVariable inequality = new LinearInequalityInOneVariable(
                        b, y, a * xEquals + c, allowEquals);
                return inequality.accept3(expressionsInY);
            }
            if (yEquals != null) {
                LinearInequalityInOneVariable inequality = new LinearInequalityInOneVariable(
                        a, x, b * yEquals + c, allowEquals);
                return inequality.accept3(expressionsInX);
            }
            // at least one inequality on x, at least one on y; they can be expressed as intervals
            Interval intervalX = Interval.extractInterval1(expressionsInX);
            Interval intervalY = Interval.extractInterval1(expressionsInY);
            if (intervalX == null || intervalY == null) return null;

            if (intervalX.isClosed() && intervalY.isClosed()) {
                // is a box
                return accept5(intervalX.left(), intervalY.left()) || accept5(intervalX.right(), intervalY.left()) ||
                        accept5(intervalX.left(), intervalY.right()) || accept5(intervalX.right(), intervalY.right());
            }
            if (intervalX.isClosed()) {
                if (intervalY.isOpenLeft()) { // looks like the capital letter PI
                    return accept5(intervalX.left(), intervalY.right()) || accept5(intervalX.right(), intervalY.right()) ||
                            isOpenLeftY();
                }
                // looks like the letter U
                return accept5(intervalX.left(), intervalY.left()) || accept5(intervalX.right(), intervalY.left()) ||
                        isOpenRightY();
            }
            if (intervalY.isClosed()) {
                if (intervalX.isOpenLeft()) { // looks like ]
                    return accept5(intervalX.right(), intervalY.left()) || accept5(intervalX.right(), intervalY.right()) ||
                            isOpenLeftX();
                }
                // looks like [
                return accept5(intervalX.left(), intervalY.left()) || accept5(intervalX.right(), intervalY.left()) ||
                        isOpenRightX();
            }
            // neither are closed; look like 90 degrees rotations of L; first, try the corner point
            if (accept5(intervalX.isOpenLeft() ? intervalX.right() : intervalX.left(), intervalY.isOpenLeft() ? intervalY.right() : intervalY.left()))
                return true;

            return intervalX.isOpenRight() && isOpenRightX() || intervalX.isOpenLeft() && isOpenLeftX()
                    || intervalY.isOpenRight() && isOpenRightY() || intervalY.isOpenLeft() && isOpenLeftY();
        }

    }


    public static boolean onlyNotEquals(List<Expression> expressions) {
        return expressions.stream().allMatch(e -> e instanceof Negation n
                && n.expression instanceof Equals eq
                && eq.lhs instanceof ConstantExpression<?> && eq.rhs.isInstanceOf(VariableExpression.class));
    }

    public static Double extractEquals(List<Expression> expressions) {
        return expressions.stream().filter(e -> e instanceof Equals eq
                        && eq.lhs instanceof ConstantExpression<?>
                        && !(eq.lhs instanceof NullConstant))
                .map(e -> extractDouble((Number) ((ConstantExpression<?>) ((Equals) e).lhs).getValue()))
                .findFirst().orElse(null);
    }

    private static double extractDouble(Number number) {
        return number.doubleValue();
    }

    static class NullConstant extends ConstantExpression<Object> implements Expression {

        public NullConstant() {
            super(null);
        }
    }

    record VariableExpression(Variable variable) implements Expression {

    }
}
