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
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;

import java.util.Objects;

@E2Container
public record DelayedExpression(String msg, String debug, ParameterizedType parameterizedType) implements Expression {

    public static DelayedExpression forMethod(MethodInfo methodInfo) {
        return new DelayedExpression("<m:" + methodInfo.name + ">",
                "<method:" + methodInfo.fullyQualifiedName + ">", methodInfo.returnType());
    }

    /*
    expression with delayed state
     */
    public static Expression forState(ParameterizedType parameterizedType) {
        return new DelayedExpression("<s:" + parameterizedType.printSimple() + ">",
                "<state:" + parameterizedType.detailedString() + ">", parameterizedType);
    }

    public static Expression forNewObject(ParameterizedType parameterizedType) {
        return new DelayedExpression("<new:" + parameterizedType.printSimple() + ">",
                "<new:" + parameterizedType.detailedString() + ">", parameterizedType);
    }

    public static Expression forArrayLength(Primitives primitives) {
        return new DelayedExpression("<delayed array length>", "<delayed array length>", primitives.intParameterizedType);
    }

    public static Expression forPrecondition(Primitives primitives) {
        return new DelayedExpression("<precondition>", "<precondition>", primitives.booleanParameterizedType);
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
        return Objects.hash(msg, parameterizedType);
    }

    @Override
    public boolean isNumeric() {
        return Primitives.isNumeric(parameterizedType.typeInfo);
    }

    @Override
    public String toString() {
        return msg;
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
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
        return new EvaluationResult.Builder(evaluationContext).setExpression(this).build();
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
        if (VariableProperty.NOT_NULL_EXPRESSION == variableProperty && Primitives.isPrimitiveExcludingVoid(parameterizedType)) {
            return MultiLevel.EFFECTIVELY_NOT_NULL;
        }
        return Level.DELAY;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return this;
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return LinkedVariables.DELAY;
    }
}
