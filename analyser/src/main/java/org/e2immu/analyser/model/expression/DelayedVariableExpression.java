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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;

import java.util.Objects;

@E2Container
public record DelayedVariableExpression(String msg, String debug,
                                        Variable variable) implements Expression, IsVariableExpression {

    public static DelayedVariableExpression forParameter(ParameterInfo parameterInfo) {
        return new DelayedVariableExpression("<p:" + parameterInfo.name + ">",
                "<parameter:" + parameterInfo.fullyQualifiedName() + ">", parameterInfo);
    }

    public static DelayedVariableExpression forField(FieldReference fieldReference) {
        return new DelayedVariableExpression("<f:" + fieldReference.fieldInfo.name + ">",
                "<field:" + fieldReference.fullyQualifiedName() + ">", fieldReference);
    }

    public static Expression forVariable(Variable variable) {
        if (variable instanceof FieldReference fieldReference) return forField(fieldReference);
        if (variable instanceof ParameterInfo parameterInfo) return forParameter(parameterInfo);
        return new DelayedVariableExpression("<v:" + variable.simpleName() + ">",
                "<variable:" + variable.fullyQualifiedName() + ">", variable);
    }

    /*
    variable fields have different values according to statement time, but then, at this point we cannot know yet
    whether the field will be variable or not.
    Basics7 shows a case where the local condition manager goes from true to false depending on this equality.
     */
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, variable.parameterizedType());
    }

    @Override
    public boolean isNumeric() {
        return Primitives.isNumeric(variable.parameterizedType().typeInfo);
    }

    @Override
    public String toString() {
        return msg;
    }

    @Override
    public ParameterizedType returnType() {
        return variable.concreteReturnType();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(msg, debug));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new EvaluationResult.Builder(evaluationContext).build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_NO_VALUE;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW;
    }

    @Override
    public boolean isDelayed(EvaluationContext evaluationContext) {
        return true;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        return Level.DELAY;
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return LinkedVariables.DELAY;
    }
}
