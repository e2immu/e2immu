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
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.*;

@E2Container
public class VariableExpression implements Expression {
    public final Variable variable;

    public VariableExpression(@NotNull Variable variable) {
        this.variable = Objects.requireNonNull(variable);
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
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return evaluate(evaluationContext, forwardEvaluationInfo, variable);
    }

    // code also used by FieldAccess
    public static EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo, Variable variable) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder();
        Value currentValue = builder.currentValue(variable, evaluationContext);

        if (forwardEvaluationInfo.isNotAssignmentTarget()) {
            builder.markRead(variable);
        }
        int notNull = forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL);
        if (notNull > MultiLevel.NULLABLE) {
            builder.variableOccursInNotNullContext(variable, currentValue, evaluationContext, notNull);
        }
        int modified = forwardEvaluationInfo.getProperty(VariableProperty.MODIFIED);
        builder.markContentModified(evaluationContext, variable, currentValue, modified);

        int notModified1 = forwardEvaluationInfo.getProperty(VariableProperty.NOT_MODIFIED_1);
        if (notModified1 == Level.TRUE) {
            builder.variableOccursInNotModified1Context(variable, currentValue, evaluationContext);
        }

        int size = forwardEvaluationInfo.getProperty(VariableProperty.SIZE);
        builder.markSizeRestriction(evaluationContext, variable, size);

        int methodCalled = forwardEvaluationInfo.getProperty(VariableProperty.METHOD_CALLED);
        builder.markMethodCalled(evaluationContext, variable, methodCalled);

        int methodDelay = forwardEvaluationInfo.getProperty(VariableProperty.METHOD_DELAY);
        builder.markMethodDelay(evaluationContext, variable, methodDelay);

        builder.setValue(currentValue);
        return builder.build();
    }

    @Override
    public ParameterizedType returnType() {
        return variable.parameterizedType();
    }

    @Override
    public String expressionString(int indent) {
        return variable.name();
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
    public Optional<Variable> assignmentTarget() {
        return Optional.of(variable);
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return variable.sideEffect(Objects.requireNonNull(evaluationContext));
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
