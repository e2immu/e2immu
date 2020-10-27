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
import java.util.Objects;


@E2Container
public class InstanceOf implements Expression {
    public final ParameterizedType parameterizedType;
    public final Expression expression;
    private final ParameterizedType booleanParameterizedType;

    public InstanceOf(Expression expression, ParameterizedType parameterizedType, ParameterizedType booleanParameterizedType) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.expression = Objects.requireNonNull(expression);
        this.booleanParameterizedType = Objects.requireNonNull(booleanParameterizedType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceOf that = (InstanceOf) o;
        return parameterizedType.equals(that.parameterizedType) &&
                expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, expression);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new InstanceOf(translationMap.translateExpression(expression),
                translationMap.translateType(parameterizedType),
                booleanParameterizedType);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult evaluationResult = expression.evaluate(evaluationContext, forwardEvaluationInfo);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(evaluationResult);
        return localEvaluation(builder, evaluationContext, evaluationResult.value);
    }

    private EvaluationResult localEvaluation(EvaluationResult.Builder builder, EvaluationContext evaluationContext, Value value) {
        if (value.isUnknown()) {
            return builder.setValue(UnknownPrimitiveValue.UNKNOWN_PRIMITIVE).build();
        }
        if (value instanceof NullValue) {
            return builder.setValue(new BoolValue(evaluationContext.getAnalyserContext().getPrimitives(), false)).build();

        }
        if (value instanceof CombinedValue combinedValue) {
            if (combinedValue.values.size() == 1) {
                return localEvaluation(builder, evaluationContext, combinedValue.values.get(0));
            }
            return builder.setValue(UnknownPrimitiveValue.UNKNOWN_PRIMITIVE).build();
        }
        if (value instanceof VariableValue) {
            Location location = evaluationContext.getLocation(this);
            Primitives primitives = evaluationContext.getAnalyserContext().getPrimitives();
            ObjectFlow objectFlow = builder.createInternalObjectFlow(location, primitives.booleanParameterizedType, Origin.RESULT_OF_OPERATOR);
            return builder.setValue(new InstanceOfValue(primitives, ((VariableValue) value).variable, parameterizedType, objectFlow)).build();
        }
        if (value instanceof Instance) {
            Primitives primitives = evaluationContext.getAnalyserContext().getPrimitives();
            EvaluationResult er = BoolValue.of(parameterizedType.isAssignableFrom(primitives, ((Instance) value).parameterizedType),
                    evaluationContext.getLocation(this), evaluationContext, Origin.RESULT_OF_OPERATOR);
            return builder.compose(er).setValue(er.value).build();
        }
        if (value instanceof MethodValue) {
            return builder.setValue(UnknownPrimitiveValue.UNKNOWN_PRIMITIVE).build(); // no clue, too deep
        }
        if (value instanceof ClassValue) {
            Primitives primitives = evaluationContext.getAnalyserContext().getPrimitives();
            EvaluationResult er = BoolValue.of(parameterizedType.isAssignableFrom(primitives, ((ClassValue) value).value),
                    evaluationContext.getLocation(this), evaluationContext, Origin.RESULT_OF_OPERATOR);
            return builder.compose(er).setValue(er.value).build();
        }
        // this error occurs with a TypeExpression, probably due to our code giving priority to types rather than
        // variable names, when you use a type name as a variable name, which is perfectly allowed in Java but is
        // horrible practice. We leave the bug for now.
        throw new UnsupportedOperationException("? have expression of " + expression.getClass() + " value is " + value + " of " + value.getClass());
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return booleanParameterizedType;
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
