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

import java.util.Arrays;

public record ConstantRange(ArrayInitializer initializer, VariableExpression variableExpression) implements Range {

    @Override
    public Expression conditions(EvaluationContext evaluationContext) {
        return Or.or(evaluationContext, Arrays.stream(initializer.multiExpression.expressions())
                .map(e -> Equals.equals(evaluationContext, variableExpression, e)).toList());
    }

    @Override
    public Expression exitState(EvaluationContext evaluationContext) {
        return new BooleanConstant(evaluationContext.getPrimitives(), true);
    }

    @Override
    public int loopCount() {
        return initializer.multiExpression.size();
    }
}
