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
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import static org.e2immu.analyser.model.Level.FALSE;

@E2Container
public class NullConstant implements ConstantExpression<Object> {
    public static final NullConstant NULL_CONSTANT = new NullConstant();
    public static final EvaluationResult NULL_RESULT = new EvaluationResult.Builder().setExpression(NULL_CONSTANT).build();

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return ParameterizedType.NULL_CONSTANT;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("null"));
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL_EXPRESSION) > MultiLevel.NULLABLE) {
            return new EvaluationResult.Builder().raiseError(Message.NULL_POINTER_EXCEPTION)
                    .setExpression(NULL_CONSTANT).build();
        }
        return NULL_RESULT;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        return switch (variableProperty) {
            case NOT_NULL_EXPRESSION -> MultiLevel.NULLABLE;
            case CONTEXT_MODIFIED, CONTEXT_MODIFIED_DELAY, IGNORE_MODIFICATIONS, NOT_MODIFIED_1, IDENTITY -> FALSE;

            // if this becomes a problem we'll have to add a parameterized type as the expression context, and
            // take the value of the parameterized type's best type analysis
            case CONTAINER, INDEPENDENT -> Level.DELAY;
            case IMMUTABLE -> MultiLevel.NOT_INVOLVED;
            default -> throw new UnsupportedOperationException("Asking for " + variableProperty);
        };
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_CONSTANT_NULL;
    }

    @Override
    public Object getValue() {
        return null;
    }
}
