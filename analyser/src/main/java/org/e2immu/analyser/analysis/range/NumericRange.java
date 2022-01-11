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
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;

// a proper range
public record NumericRange(int startIncl,
                           int endExcl,
                           int increment,
                           VariableExpression variableExpression) implements Range {

    public NumericRange {
        assert startIncl < endExcl && increment > 0 || startIncl > endExcl && increment < 0;
    }

    @Override
    public Expression conditions(EvaluationContext evaluationContext) {
        int count = loopCount();
        IntConstant start = new IntConstant(evaluationContext.getPrimitives(), startIncl);
        if (count == 1) {
            return Equals.equals(evaluationContext, start, variableExpression);
        }
        IntConstant end = new IntConstant(evaluationContext.getPrimitives(), endExcl);
        Expression geStart = GreaterThanZero.greater(evaluationContext, variableExpression, start, true);
        Expression ltEnd = GreaterThanZero.less(evaluationContext, variableExpression, end, false);
        return And.and(evaluationContext, geStart, ltEnd);
    }

    @Override
    public int loopCount() {
        return (int) Math.ceil((endExcl - startIncl) / (double) increment);
    }
}
