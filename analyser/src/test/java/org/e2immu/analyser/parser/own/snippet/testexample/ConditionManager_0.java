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


import java.util.List;
import java.util.Objects;

public class ConditionManager_0 {

    interface Primitives {
    }

    interface Expression {
        boolean isUnknown();
    }

    static class BooleanConstant implements Expression {
        private final boolean value;

        public BooleanConstant(Primitives primitives, boolean value) {
            this.value = value;
        }

        @Override
        public boolean isUnknown() {
            return value;
        }
    }

    record Precondition(Expression expression, List<String> causes) {
        public static Precondition empty(BooleanConstant bc) {
            return new Precondition(bc, List.of());
        }
    }

    record UnknownExpression() implements Expression {
        private static final UnknownExpression SPECIAL = new UnknownExpression();

        static UnknownExpression forSpecial() {
            return SPECIAL;
        }

        @Override
        public boolean isUnknown() {
            return false;
        }
    }

    public record ConditionManager(Expression condition,
                                   Expression state,
                                   Precondition precondition,
                                   ConditionManager parent) {

        public static final ConditionManager SPECIAL = new ConditionManager();

        private ConditionManager() {
            this(UnknownExpression.forSpecial(), UnknownExpression.forSpecial(), new Precondition(UnknownExpression.forSpecial(), List.of()), null);
        }

        public ConditionManager {
            checkBooleanOrUnknown(Objects.requireNonNull(condition));
            checkBooleanOrUnknown(Objects.requireNonNull(state));
            Objects.requireNonNull(precondition);
        }

        private static void checkBooleanOrUnknown(Expression v) {
            if (!v.isUnknown()) {
                throw new UnsupportedOperationException("Need an unknown or boolean value in the condition manager; got " + v);
            }
        }

        public static ConditionManager initialConditionManager(Primitives primitives) {
            BooleanConstant TRUE = new BooleanConstant(primitives, true);
            return new ConditionManager(TRUE, TRUE,
                    Precondition.empty(TRUE), null);
        }

        public static ConditionManager impossibleConditionManager(Primitives primitives) {
            BooleanConstant FALSE = new BooleanConstant(primitives, true);
            return new ConditionManager(FALSE, FALSE,
                    new Precondition(FALSE, List.of()), null);
        }
    }
}
