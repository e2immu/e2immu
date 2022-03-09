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

// a mix of ECI and preconditions, modelled on ConditionManager
// does not yet cause the crash...

import java.util.Objects;

public class ExplicitConstructorInvocation_10 {

    interface Expression {
        boolean isBooleanValue();

        boolean other();

        Expression merge(Expression condition);
    }

    record UnknownExpression(boolean v) implements Expression {
        static final UnknownExpression E1 = new UnknownExpression(true);
        static final UnknownExpression E2 = new UnknownExpression(false);

        @Override
        public boolean isBooleanValue() {
            return v;
        }

        @Override
        public boolean other() {
            return false;
        }

        @Override
        public Expression merge(Expression condition) {
            return new UnknownExpression(v || condition.other());
        }
    }

    record C(Expression condition, Expression state, Expression precondition, C parent) {

        public static final C CC = new C();

        private C() {
            this(UnknownExpression.E1, UnknownExpression.E2, null, null);
        }

        C {
            ensure(Objects.requireNonNull(condition));
            ensure(Objects.requireNonNull(state));
            Objects.requireNonNull(precondition);
        }

        private static void ensure(Expression expression) {
            if (!expression.isBooleanValue() && expression.other()) throw new UnsupportedOperationException();
        }

        public Expression absolute() {
            return parent == null ? condition : condition.merge(parent.condition);
        }
    }
}
