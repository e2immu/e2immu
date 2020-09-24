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
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

@E2Container
public class NullConstant implements Expression, Constant<Object> {
    public static final NullConstant NULL_CONSTANT = new NullConstant();
    public static final EvaluationResult NULL_RESULT = new EvaluationResult.Builder().setValue(NullValue.NULL_VALUE).build();

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
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL) > MultiLevel.NULLABLE) {
            return new EvaluationResult.Builder().raiseError(Message.NULL_POINTER_EXCEPTION).setValue(NullValue.NULL_VALUE).build();
        }
        return NULL_RESULT;
    }

    @Override
    public Object getValue() {
        return null;
    }
}
