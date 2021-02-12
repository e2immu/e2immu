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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;

import java.util.Objects;

/**
 * Contains "some value".
 */
public record UnknownExpression(ParameterizedType parameterizedType, String msg) implements Expression {

    public static final String RETURN_VALUE = "return value";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnknownExpression that = (UnknownExpression) o;
        return msg.equals(that.msg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg);
    }

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
            case NOT_NULL_EXPRESSION:
            case NOT_NULL_VARIABLE:
                return MultiLevel.EFFECTIVELY_NOT_NULL;
            case CONTEXT_MODIFIED:
            case CONTEXT_MODIFIED_DELAY:
            case IDENTITY:
                return Level.FALSE;
        }
        throw new UnsupportedOperationException("No info about " + variableProperty + " for primitive");
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("<" + msg + ">", "<" + msg + ":" + parameterizedType.output(qualification) + ">"));
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
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW;
    }
}
