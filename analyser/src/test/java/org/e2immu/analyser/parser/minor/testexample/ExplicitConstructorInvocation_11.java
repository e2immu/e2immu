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

import java.util.Objects;

public class ExplicitConstructorInvocation_11 {
    interface Expression {
        int getComplexity();
    }

    enum Precedence {
        HIGH, LOW, AND
    }

    record MethodInfo(String name, Expression expression) {
        int getComplexity() {
            return 1 + expression.getComplexity();
        }
    }

    record Primitives(Expression bitwiseAnd) {
        public MethodInfo bitwiseAndOperatorInt() {
            return new MethodInfo("&", bitwiseAnd);
        }
    }

    record Identifier() {
    }

    static abstract class ElementImpl {

        public final Identifier identifier;

        protected ElementImpl(Identifier identifier) {
            this.identifier = identifier;
        }

        public Identifier getIdentifier() {
            return identifier;
        }
    }

    static class BaseExpression extends ElementImpl implements Expression {
        private final int complexity;

        protected BaseExpression(Identifier identifier, int complexity) {
            super(identifier);
            this.complexity = complexity;
        }

        public int getComplexity() {
            return complexity;
        }

        public Identifier getIdentifier() {
            return identifier;
        }
    }

    static class BinaryOperator extends BaseExpression implements Expression {
        protected final Primitives primitives;
        public final Expression lhs;
        public final Expression rhs;
        public final Precedence precedence;
        public final MethodInfo operator;

        public BinaryOperator(Identifier identifier,
                              Primitives primitives, Expression lhs, MethodInfo operator, Expression rhs, Precedence precedence) {
            super(identifier, operator.getComplexity() + lhs.getComplexity() + rhs.getComplexity());
            this.lhs = Objects.requireNonNull(lhs);
            this.rhs = Objects.requireNonNull(rhs);
            this.precedence = precedence;
            this.operator = Objects.requireNonNull(operator);
            this.primitives = primitives;
        }
    }

    static class BitwiseAnd extends BinaryOperator {

        private BitwiseAnd(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
            super(identifier, primitives, lhs, primitives.bitwiseAndOperatorInt(), rhs, Precedence.AND);
        }
    }
}
