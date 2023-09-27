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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.context.impl.EvaluationResultImpl;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;

/**
 * a proper range: start <= i < endExcl when i>0, and endExcl < i <= startExcl when i < 0.
 */

public record NumericRange(int startIncl,
                           int endExcl,
                           int increment,
                           VariableExpression variableExpression) implements Range {

    public NumericRange {
        assert startIncl < endExcl && increment > 0 || startIncl > endExcl && increment < 0;
    }

    /**
     * Reason for this system is that these exits play havoc with exitState() and the normal condition (see Range_4).
     * Rather than try to program for it, we raise and error and ignore them semantically.
     * I doubt these things appear in real code; if anything they're there for temporary testing.
     *
     * @param expression the condition of the interrupt, such as i==3
     * @return true when this exit breaks the expected range, e.g. if the range is 0...20, then true for i==3 but false for i+unknown==3
     */
    public boolean generateErrorOnInterrupt(Expression expression) {
        return expression instanceof Equals equals && variableExpression.equals(equals.rhs) && equals.lhs instanceof IntConstant;
        // if the variable is not within the conditions, we'll get a different sort of error
        // TODO can be more complex, but is this useful?
    }

    // exitValue is not used in the situation of "breaks" or "returns" in the loop
    // we fall back on the exit state, but only for variables that exist after the loop
    @Override
    public Expression exitState(EvaluationContext evaluationContext) {
        if (variableExpression.getSuffix() instanceof VariableExpression.VariableInLoop) {
            Expression exitValue = exitValue(evaluationContext.getPrimitives(), variableExpression.variable());
            assert exitValue != null;
            return Equals.equals(EvaluationResultImpl.from(evaluationContext), variableExpression, exitValue);
        }
        return new BooleanConstant(evaluationContext.getPrimitives(), true);
    }

    @Override
    public Expression exitValue(Primitives primitives, Variable variable) {
        if (variableExpression.variable().equals(variable)) {
            int absIncrement = increment < 0 ? -increment : increment;
            int diff = increment < 0 ? startIncl - endExcl : endExcl - startIncl;
            int moduloOfStart = diff % absIncrement;
            int value = endExcl + (increment > 0 ? moduloOfStart : 1 - moduloOfStart);
            return new IntConstant(primitives, variableExpression.identifier, value);
        }
        return null;
    }

    @Override
    public Expression conditions(EvaluationContext evaluationContext) {
        EvaluationResult context = EvaluationResultImpl.from(evaluationContext);
        Primitives primitives = context.getPrimitives();
        Identifier identifier = evaluationContext.getLocation(Stage.EVALUATION).identifier();

        int count = loopCount();
        IntConstant start = new IntConstant(primitives, identifier, startIncl);
        if (count == 1) {
            return Equals.equals(context, start, variableExpression);
        }
        IntConstant end = new IntConstant(primitives, identifier, endExcl);
        Expression geStart;
        Expression ltEnd;
        if (startIncl < endExcl) {
            geStart = GreaterThanZero.greater(context, variableExpression, start, true);
            ltEnd = GreaterThanZero.less(context, variableExpression, end, false);
        } else {
            geStart = GreaterThanZero.less(context, variableExpression, start, true);
            ltEnd = GreaterThanZero.greater(context, variableExpression, end, false);
        }
        int absIncrement = increment < 0 ? -increment : increment;
        if (absIncrement == 1) {
            return And.and(context, geStart, ltEnd);
        }

        // if increment == 2, we have either odd or even values, depending on the start value
        // so we add (i % increment)==modOfStart
        int moduloOfStart = startIncl % absIncrement;
        Expression modulo = Remainder.remainder(context, variableExpression,
                new IntConstant(primitives, identifier, absIncrement));
        Expression moduloEquals = Equals.equals(context, modulo,
                new IntConstant(primitives, identifier, moduloOfStart));
        return And.and(context, geStart, ltEnd, moduloEquals);
    }

    @Override
    public int loopCount() {
        return (int) Math.ceil((endExcl - startIncl) / (double) increment);
    }
}
