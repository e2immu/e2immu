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

public class ExplicitConstructorInvocation_13 {

    interface Primitives {
    }

    record MethodInfo(String name) {
    }

    interface Expression {
        Identifier getIdentifier();
    }

    interface Variable {
        Identifier getIdentifier();
    }

    record Identifier(String id) {
    }

    record VariableExpression(Variable variable) implements Expression {
        @Override
        public Identifier getIdentifier() {
            return variable.getIdentifier();
        }
    }

    static class BaseExpression {
        protected final Identifier identifier;

        BaseExpression(Identifier identifier) {
            this.identifier = identifier;
        }

        public Identifier getIdentifier() {
            return identifier;
        }
    }

    static class Assignment extends BaseExpression implements Expression {

        public final Expression target;
        public final Expression value;
        public final MethodInfo assignmentOperator;
        public final MethodInfo binaryOperator;
        private final Primitives primitives;
        // see the discussion at DependentVariable
        public final Variable variableTarget;

        // if null, and binary operator not null, then the primitive operator counts (i += value)
        // if true, we have ++i
        // if false, we have i++
        public final Boolean prefixPrimitiveOperator;
        public final boolean complainAboutAssignmentOutsideType;
        public final boolean hackForUpdatersInForLoop;

        private Assignment(Identifier identifier,
                           Primitives primitives,
                           Expression target,
                           Expression value,
                           MethodInfo assignmentOperator,
                           Boolean prefixPrimitiveOperator,
                           boolean complain2,
                           Variable variableTarget,
                           MethodInfo binaryOperator,
                           boolean hackForUpdatersInForLoop) {
            super(identifier);
            this.primitives = primitives;
            this.target = target;
            this.value = value;
            this.assignmentOperator = assignmentOperator;
            this.prefixPrimitiveOperator = prefixPrimitiveOperator;
            this.complainAboutAssignmentOutsideType = complain2;
            this.variableTarget = variableTarget;
            this.binaryOperator = binaryOperator;
            this.hackForUpdatersInForLoop = hackForUpdatersInForLoop;
        }

        // see explanation below (makeHackInstance); called in SAInitializersAndUpdaters
        public Expression cloneWithHackForLoop() {
            return new Assignment(identifier, primitives, target, value, assignmentOperator, prefixPrimitiveOperator,
                    complainAboutAssignmentOutsideType, variableTarget, binaryOperator, true);
        }

        public Assignment(Primitives primitives, Expression target, Expression value) {
            this(target.getIdentifier(), primitives,
                    target, value, null, null, true);
        }

        public Assignment(Identifier identifier, Primitives primitives, Expression target, Expression value) {
            this(identifier, primitives, target, value, null, null, true);
        }

        public Assignment(Identifier identifier,
                          Primitives primitives,
                          Expression target, Expression value,
                          MethodInfo assignmentOperator,
                          Boolean prefixPrimitiveOperator,
                          boolean complain1) {
            super(identifier);
            this.complainAboutAssignmentOutsideType = complain1;
            this.target = Objects.requireNonNull(target);
            this.value = Objects.requireNonNull(value);
            this.assignmentOperator = assignmentOperator; // as in i+=1;
            this.prefixPrimitiveOperator = prefixPrimitiveOperator;
            binaryOperator = assignmentOperator == null ? null : new MethodInfo("abc");
            this.primitives = primitives;
            if (target instanceof VariableExpression ve) {
                variableTarget = ve.variable();
            } else {
                throw new UnsupportedOperationException();
            }
            hackForUpdatersInForLoop = false;
        }
    }
}
