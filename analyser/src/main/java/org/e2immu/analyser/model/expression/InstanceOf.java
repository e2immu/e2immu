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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.ClassValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.List;


@E2Container
public class InstanceOf implements Expression {
    public final ParameterizedType parameterizedType;
    public final Expression expression;

    public InstanceOf(Expression expression, ParameterizedType parameterizedType) {
        this.parameterizedType = parameterizedType;
        this.expression = expression;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new InstanceOf(translationMap.translateExpression(expression), translationMap.translateType(parameterizedType));
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult evaluationResult = expression.evaluate(evaluationContext, forwardEvaluationInfo);
        return new EvaluationResult.Builder(evaluationContext)
                .compose(evaluationResult)
                .setValue(localEvaluation(evaluationContext, evaluationResult.value))
                .build();
    }

    private Value localEvaluation(EvaluationContext evaluationContext, Value value) {
        Value result;
        if (value.isUnknown()) {
            result = UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        } else if (value instanceof NullValue) {
            result = BoolValue.FALSE;
        } else if (value instanceof CombinedValue) {
            CombinedValue combinedValue = (CombinedValue) value;
            if (combinedValue.values.size() == 1) {
                return localEvaluation(evaluationContext, combinedValue.values.get(0));
            }
            result = UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        } else if (value instanceof ValueWithVariable) {
            ObjectFlow objectFlow = new ObjectFlow(evaluationContext.getLocation(), Primitives.PRIMITIVES.booleanParameterizedType, Origin.RESULT_OF_OPERATOR);
            result = new InstanceOfValue(((ValueWithVariable) value).variable, parameterizedType, objectFlow);
        } else if (value instanceof Instance) {
            result = BoolValue.of(parameterizedType.isAssignableFrom(((Instance) value).parameterizedType), evaluationContext, Origin.RESULT_OF_OPERATOR);
        } else if (value instanceof MethodValue) {
            result = UnknownPrimitiveValue.UNKNOWN_PRIMITIVE; // no clue, too deep
        } else if (value instanceof ClassValue) {
            result = BoolValue.of(parameterizedType.isAssignableFrom(((ClassValue) value).value), evaluationContext, Origin.RESULT_OF_OPERATOR);
        } else {
            // this error occurs with a TypeExpression, probably due to our code giving priority to types rather than
            // variable names, when you use a type name as a variable name, which is perfectly allowed in Java but is
            // horrible practice. We leave the bug for now.
            throw new UnsupportedOperationException("? have expression of " + expression.getClass() + " value is " + value + " of " + value.getClass());
        }
        return result;
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return Primitives.PRIMITIVES.booleanParameterizedType;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return bracketedExpressionString(indent, expression) + " instanceof " + parameterizedType.stream();
    }

    @Override
    public int precedence() {
        return 9;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(expression.typesReferenced(), parameterizedType.typesReferenced(true));
    }
}
