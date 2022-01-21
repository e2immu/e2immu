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

package org.e2immu.analyser.parser.minor.testexample;

/*
Part of own code (GreaterThanZero), first extracted because of instance pattern problems.
Useful for infinite loop detection: the field "InstanceOf_11.expression" is
dependent on the properties of InstanceOf_11 itself, because it is of an (anonymous) inner class.

 */

public class InstanceOf_11 {

    private final Expression expression = new Expression() {
    };

    private interface Expression {
    }

    private interface EvaluationContext {
        Expression getExpression();
    }

    private record Negation(Expression expression) implements Expression {
    }

    private record Sum(Expression lhs, Expression rhs) implements Expression {
        public Double numericPartOfLhs() {
            return lhs.equals(rhs) ? 3.0 : null;
        }

        public Expression nonNumericPartOfLhs(EvaluationContext evaluationContext) {
            return lhs;
        }
    }


    public record XB(Expression x, double b, boolean lessThan) {
    }


    public XB method(EvaluationContext evaluationContext) {
        if (expression instanceof Sum sum) {
            Double d = sum.numericPartOfLhs();
            if (d != null) {
                Expression v = sum.nonNumericPartOfLhs(evaluationContext);
                Expression x;
                boolean lessThan;
                double b;
                if (v instanceof Negation ne1) {
                    x = ne1.expression;
                    lessThan = true;
                    b = d;
                } else {
                    x = v;
                    lessThan = false;
                    b = -d;
                }
                return new XB(x, b, lessThan);
            }
        }
        Expression x;
        boolean lessThan;
        if (expression instanceof Negation ne) {
            x = ne.expression;
            lessThan = true;
        } else {
            x = expression;
            lessThan = false;
        }
        return new XB(x, 0.0d, lessThan);
    }
}
