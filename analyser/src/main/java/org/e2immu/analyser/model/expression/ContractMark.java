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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.output.OutputBuilder;

import java.util.Set;

/**
 * Specifically used to transfer @Mark(" ...") at CONTRACT level.
 */
public record ContractMark(Set<FieldInfo> fields) implements Expression {

    @Override
    public ParameterizedType returnType() {
        return fields.stream().findFirst().orElseThrow().type;
    }

    @Override
    public Precedence precedence() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return null;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_NO_VALUE;
    }

    @Override
    public String toString() {
        return fields().toString();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        return Level.FALSE;
    }
}
