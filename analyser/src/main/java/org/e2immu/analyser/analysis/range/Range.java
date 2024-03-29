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

package org.e2immu.analyser.analysis.range;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.MultiExpressions;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;

public interface Range {
    /**
     * express the range conditions in terms of the loop variable
     *
     * @return if "i" is the variable, a boolean expression like 0<=i && i<=10
     */
    Expression conditions(EvaluationContext evaluationContext);

    Expression exitState(EvaluationContext evaluationContext);

    default Expression exitValue(Primitives primitives, Variable variable) {
        return null;
    }

    default CausesOfDelay causesOfDelay() {
        return CausesOfDelay.EMPTY;
    }

    default boolean generateErrorOnInterrupt(Expression expression) {
        return false;
    }

    default boolean isDelayed() {
        return causesOfDelay().isDelayed();
    }

    int NO_IDEA = -1;
    int INFINITE = -2;

    /**
     * @return will the body of the loop be executed at all?
     */
    int loopCount();

    Range EMPTY = new Range() {
        @Override
        public Expression conditions(EvaluationContext evaluationContext) {
            return new BooleanConstant(evaluationContext.getPrimitives(), false);
        }

        @Override
        public Expression exitState(EvaluationContext evaluationContext) {
            return new BooleanConstant(evaluationContext.getPrimitives(), true);
        }

        @Override
        public int loopCount() {
            return 0;
        }

        @Override
        public String toString() {
            return "EMPTY";
        }
    };

    Range INFINITE_LOOP = new Range() {
        @Override
        public Expression conditions(EvaluationContext evaluationContext) {
            return new BooleanConstant(evaluationContext.getPrimitives(), true);
        }

        @Override
        public Expression exitState(EvaluationContext evaluationContext) {
            return new BooleanConstant(evaluationContext.getPrimitives(), false);
        }

        @Override
        public int loopCount() {
            return INFINITE;
        }

        @Override
        public String toString() {
            return "INFINITE";
        }
    };

    Range NO_RANGE = new Range() {
        @Override
        public Expression conditions(EvaluationContext evaluationContext) {
            return new BooleanConstant(evaluationContext.getPrimitives(), true);
        }

        @Override
        public Expression exitState(EvaluationContext evaluationContext) {
            return new BooleanConstant(evaluationContext.getPrimitives(), true);
        }

        @Override
        public int loopCount() {
            return NO_IDEA;
        }

        @Override
        public String toString() {
            return "NO RANGE";
        }
    };

    default List<Variable> variables() {
        return List.of();
    }

    default Expression expression(Identifier identifier) {
        return MultiExpressions.from(identifier, variables());
    }

    record Delayed(CausesOfDelay causesOfDelay, Expression original) implements Range {

        @Override
        public Expression conditions(EvaluationContext evaluationContext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression exitState(EvaluationContext evaluationContext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int loopCount() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "DELAYED RANGE";
        }

        @Override
        public List<Variable> variables() {
            return original.variables(true);
        }

        @Override
        public Expression expression(Identifier identifier) {
            return original;
        }
    }
}
