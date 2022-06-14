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

public class ExplicitConstructorInvocation_12_1 {
    interface Expression {
        int getComplexity();
    }

    record Identifier() {
    }

    static abstract class X1_ElementImpl {

        public final Identifier identifier;

        protected X1_ElementImpl(Identifier identifier1) {
            this.identifier = identifier1;
        }

        public Identifier getIdentifier() {
            return identifier;
        }
    }

    static class X5_BaseExpression extends X1_ElementImpl implements Expression {
        private final int complexity;

        protected X5_BaseExpression(Identifier identifier5, int complexity) {
            super(identifier5);
            this.complexity = complexity;
        }

        public int getComplexity() {
            return complexity;
        }

        public Identifier getIdentifier() {
            return identifier;
        }
    }

    static class X3_BinaryOperator extends X5_BaseExpression implements Expression {

        public X3_BinaryOperator(Identifier identifier3) {
            super(identifier3, 0);
        }
    }
}
