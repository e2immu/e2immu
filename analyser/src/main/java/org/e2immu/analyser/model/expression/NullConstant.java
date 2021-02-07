/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import static org.e2immu.analyser.model.Level.FALSE;
import static org.e2immu.analyser.model.Level.TRUE;

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
        if (forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL) > MultiLevel.NULLABLE) {
            return new EvaluationResult.Builder().raiseError(Message.NULL_POINTER_EXCEPTION)
                    .setExpression(NULL_CONSTANT).build();
        }
        return NULL_RESULT;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return switch (variableProperty) {
            case NOT_NULL -> MultiLevel.NULLABLE;
            case MODIFIED, METHOD_DELAY, IGNORE_MODIFICATIONS, NOT_MODIFIED_1, IDENTITY -> FALSE;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case CONTAINER -> TRUE;
            default -> throw new UnsupportedOperationException("Asking for " + variableProperty);
        };
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_CONSTANT_NULL;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW;
    }

    @Override
    public Object getValue() {
        return null;
    }
}
