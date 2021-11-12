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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import static org.e2immu.analyser.model.Level.FALSE_DV;

@E2Container
public class NullConstant implements ConstantExpression<Object> {
    public static final NullConstant NULL_CONSTANT = new NullConstant();

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
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        DV max = forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL_EXPRESSION).max(
                forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_NOT_NULL));
        if (max.gt(MultiLevel.NULLABLE_DV)) {
            builder.raiseError(getIdentifier(), Message.Label.NULL_POINTER_EXCEPTION);
        }
        return builder.setExpression(NULL_CONSTANT).build();
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        return switch (variableProperty) {
            case NOT_NULL_EXPRESSION -> MultiLevel.NULLABLE_DV;
            case CONTEXT_MODIFIED, IGNORE_MODIFICATIONS, IDENTITY, CONTAINER -> FALSE_DV;
            case IMMUTABLE, INDEPENDENT -> MultiLevel.NOT_INVOLVED_DV;
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

    @Override
    public Identifier getIdentifier() {
        return Identifier.CONSTANT;
    }
}
