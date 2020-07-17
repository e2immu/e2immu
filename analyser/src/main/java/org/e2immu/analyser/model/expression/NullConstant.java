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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;

@E2Immutable
public class NullConstant implements Expression, Constant<Object> {
    public static final NullConstant nullConstant = new NullConstant();

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return ParameterizedType.NULL_CONSTANT;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return "null";
    }

    @Override
    public int precedence() {
        return 17;
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL) > MultiLevel.NULLABLE) {
            evaluationContext.raiseError(Message.NULL_POINTER_EXCEPTION);
        }
        Value result = NullValue.NULL_VALUE;
        visitor.visit(this, evaluationContext, result);
        return result;
    }

    @Override
    public Object getValue() {
        return null;
    }
}
