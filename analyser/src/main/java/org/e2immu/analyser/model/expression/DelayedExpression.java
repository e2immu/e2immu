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

@E2Container
public record DelayedExpression(String msg, ParameterizedType parameterizedType) implements Expression {

    public static DelayedExpression forMethod(MethodInfo methodInfo) {
        return new DelayedExpression("<method:" + methodInfo.fullyQualifiedName + ">", methodInfo.returnType());
    }

    public static DelayedExpression forParameter(ParameterInfo parameterInfo) {
        return new DelayedExpression("<parameter:" + parameterInfo.fullyQualifiedName() + ">", parameterInfo.parameterizedType());
    }

    public static DelayedExpression forField(FieldInfo fieldInfo) {
        return new DelayedExpression("<field:" + fieldInfo.fullyQualifiedName() + ">", fieldInfo.type);
    }

    public static Expression forCondition(Primitives primitives) {
        return new DelayedExpression("<condition>", primitives.booleanParameterizedType);
    }

    public static Expression forVariable(Variable variable) {
        if (variable instanceof FieldReference fieldReference) return forField(fieldReference.fieldInfo);
        if (variable instanceof ParameterInfo parameterInfo) return forParameter(parameterInfo);
        return new DelayedExpression("<variable:" + variable.fullyQualifiedName() + ">", variable.parameterizedType());
    }

    public static Expression forState(Primitives primitives) {
        return new DelayedExpression("<state>", primitives.booleanParameterizedType);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
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
    public OutputBuilder output() {
        return new OutputBuilder().add(new Text(msg, msg));
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
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return Level.DELAY;
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return LinkedVariables.DELAY;
    }
}
