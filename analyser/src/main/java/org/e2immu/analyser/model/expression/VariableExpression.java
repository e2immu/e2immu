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
import org.e2immu.analyser.model.value.Instance;
import org.e2immu.analyser.model.value.VariableValue;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Objects;

@E2Container
public record VariableExpression(Variable variable,
                                 String name,
                                 boolean variableField,
                                 ObjectFlow objectFlow) implements Expression {

    public VariableExpression(Variable variable) {
        this(variable, ObjectFlow.NO_FLOW);
    }

    public VariableExpression(Variable variable, ObjectFlow objectFlow) {
        this(variable, objectFlow, false);
    }

    public VariableExpression(Variable variable, ObjectFlow objectFlow, boolean variableField) {
        this(variable, variable.fullyQualifiedName(), variableField, objectFlow);
    }

    @Override
    public ParameterizedType type() {
        return variable.concreteReturnType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariableValue that)) return false;
        if (!variable.equals(that.variable)) return false;
        return !variableField;
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        Variable inMap = translationMap.variables.get(variable);
        if (inMap != null) {
            return new VariableExpression(inMap);
        }
        return this;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_VARIABLE_VALUE;
    }

    @Override
    public int internalCompareTo(Expression v) {
        VariableExpression variableValue = (VariableExpression) v;
        return name.compareTo(variableValue.name);
    }

    @Override
    public boolean isNumeric() {
        TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
        return Primitives.isNumeric(typeInfo);
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        return evaluationContext.currentInstance(variable);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return evaluate(evaluationContext, forwardEvaluationInfo, variable);
    }

    // code also used by FieldAccess
    public static EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo, Variable variable) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        Expression currentValue = builder.currentExpression(variable);
        builder.setExpression(currentValue);

        // no statement analyser... we're in the shallow analyser
        if (evaluationContext.getCurrentStatement() == null) return builder.build();

        if (forwardEvaluationInfo.isNotAssignmentTarget()) {
            builder.markRead(variable, evaluationContext.getIteration());
        }

        int notNull = forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL);
        if (notNull > MultiLevel.NULLABLE) {
            builder.variableOccursInNotNullContext(variable, currentValue, notNull);
        }
        int modified = forwardEvaluationInfo.getProperty(VariableProperty.MODIFIED);
        if (modified != Level.DELAY) {
            builder.markContentModified(variable, modified);
        }

        int notModified1 = forwardEvaluationInfo.getProperty(VariableProperty.NOT_MODIFIED_1);
        if (notModified1 == Level.TRUE) {
            builder.variableOccursInNotModified1Context(variable, currentValue);
        }

        int methodCalled = forwardEvaluationInfo.getProperty(VariableProperty.METHOD_CALLED);
        if (methodCalled == Level.TRUE) {
            builder.markMethodCalled(variable, methodCalled);
        }

        int methodDelay = forwardEvaluationInfo.getProperty(VariableProperty.METHOD_DELAY);
        if (methodDelay != Level.DELAY) {
            builder.markMethodDelay(variable, methodDelay);
        }

        return builder.build();
    }

    @Override
    public ParameterizedType returnType() {
        return variable.parameterizedType();
    }

    @Override
    public String expressionString(int indent) {
        return variable.simpleName();
    }

    @Override
    public int precedence() {
        return 17;
    }

    @Override
    public List<Variable> variables() {
        return List.of(variable);
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return variable.sideEffect(Objects.requireNonNull(evaluationContext));
    }

    @Override
    public String print(PrintMode printMode) {
        if (printMode.forAnnotations()) {
            if (variable instanceof ParameterInfo parameterInfo) return parameterInfo.name;
            if (variable instanceof FieldReference fieldReference) {
                String scope;
                if (fieldReference.scope == null) {
                    scope = fieldReference.fieldInfo.owner.simpleName;
                } else {
                    scope = fieldReference.scope.print(printMode);
                }
                return scope + "." + fieldReference.fieldInfo.name;
            }
        }
        return name;
    }

    @Override
    public String toString() {
        return expressionString(0);
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return variable.typesReferenced(false);
    }
}
