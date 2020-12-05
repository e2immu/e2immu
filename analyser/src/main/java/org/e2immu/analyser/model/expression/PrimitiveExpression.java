/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;

public record PrimitiveExpression(String msg) implements Expression {

    public static final PrimitiveExpression PRIMITIVE_EXPRESSION = new PrimitiveExpression("<unknown primitive>");

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return primitiveGetProperty(variableProperty);
    }

    public static int primitiveGetProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case CONTAINER:
                return Level.TRUE;
            case NOT_NULL:
                return MultiLevel.EFFECTIVELY_NOT_NULL;
            case MODIFIED:
            case METHOD_DELAY:
            case IDENTITY:
                return Level.FALSE;
        }
        throw new UnsupportedOperationException("No info about " + variableProperty + " for primitive");
    }

    @Override
    public ParameterizedType returnType() {
        return null;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output() {
        return new OutputBuilder().add(new Text("", msg));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return null;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public NewObject getInstance(EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW;
    }
}
