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

package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InequalityHelper {

    private record Term(double a, OneVariable v) {
    }

    public static Inequality extract(GreaterThanZero gt0) {
        List<Term> terms = new ArrayList<>();
        if (!recursivelyCollectTerms(gt0.expression(), terms)) return null;
        List<Term> withoutVariable = terms.stream().filter(t -> t.v == null).collect(Collectors.toUnmodifiableList());
        if (withoutVariable.size() > 1) return null;
        double c = withoutVariable.isEmpty() ? 0.0 : withoutVariable.get(0).a;

        List<Term> withVariable = terms.stream().filter(t -> t.v != null).collect(Collectors.toUnmodifiableList());
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

    private static boolean recursivelyCollectTerms(Expression expression,
                                                   List<Term> terms) {
        OneVariable oneVariableRhs;
        if (expression instanceof Product product &&
                product.lhs instanceof ConstantExpression<?> ce
                && ((oneVariableRhs = extractOneVariable(product.rhs)) != null)) {
            terms.add(new Term(extractDouble((Number) ce.getValue()), oneVariableRhs));
            return true;
        }
        if (expression instanceof Sum sum) {
            if (!recursivelyCollectTerms(sum.lhs, terms)) return false;
            return recursivelyCollectTerms(sum.rhs, terms);
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

    private static OneVariable extractOneVariable(Expression expression) {
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) return ve.variable();
        if (expression instanceof MethodCall mc && mc.object.isInstanceOf(VariableExpression.class)) {
            return mc;
        }
        return null;
    }

    private static double extractDouble(Number number) {
        return number.doubleValue();
    }


    public static boolean onlyNotEquals(List<Expression> expressions) {
        return expressions.stream().allMatch(e -> e instanceof Negation n
                && n.expression instanceof Equals eq
                && eq.lhs instanceof ConstantExpression<?> && eq.rhs.isInstanceOf(VariableExpression.class));
    }

    public static Double extractEquals(List<Expression> expressions) {
        return expressions.stream().filter(e -> e instanceof Equals eq && eq.lhs instanceof ConstantExpression<?>)
                .map(e -> extractDouble((Number) ((ConstantExpression<?>) ((Equals) e).lhs).getValue()))
                .findFirst().orElse(null);
    }

}
