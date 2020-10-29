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
import org.e2immu.analyser.model.abstractvalue.ArrayValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * We do not override variablesMarkRead(), because both the array and the index will be read in the initial
 * evaluations; so an empty list is what is needed.
 */
@E2Container
public class ArrayAccess implements Expression {

    public final Expression expression;
    public final Expression index;
    public final DependentVariable variableTarget; // can be null
    public final ParameterizedType returnType;

    public ArrayAccess(@NotNull Expression expression, @NotNull Expression index) {
        this.expression = Objects.requireNonNull(expression);
        this.index = Objects.requireNonNull(index);
        this.returnType = expression.returnType().copyWithOneFewerArrays();
        variableTarget = arrayAccessVariableTarget(expression, index, returnType);
    }

    private static DependentVariable arrayAccessVariableTarget(Expression expression, Expression index, ParameterizedType returnType) {
        Variable arrayVariable = singleVariable(expression);
        Variable indexVariable = singleVariable(index);
        String name = (arrayVariable == null ? expression.expressionString(0) : arrayVariable.fullyQualifiedName())
                + "[" + (indexVariable == null ? index.expressionString(0) : indexVariable.fullyQualifiedName()) + "]";
        return new DependentVariable(name, returnType, indexVariable == null ? List.of() : List.of(indexVariable), arrayVariable);
    }

    private static Variable singleVariable(Expression expression) {
        if (expression instanceof VariableExpression variableExpression) {
            return variableExpression.variable;
        }
        if (expression instanceof FieldAccess fieldAccess) {
            return fieldAccess.variable;
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayAccess that = (ArrayAccess) o;
        return expression.equals(that.expression) &&
                index.equals(that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, index);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new ArrayAccess(translationMap.translateExpression(expression), translationMap.translateExpression(index));
    }

    @Override
    public ParameterizedType returnType() {
        return returnType;
    }

    @Override
    public String expressionString(int indent) {
        return bracketedExpressionString(indent, expression) + "[" + index.expressionString(indent) + "]";
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression, index);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult array = expression.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL);
        EvaluationResult indexValue = index.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(array, indexValue);

        if (array.value instanceof ArrayValue arrayValue && indexValue instanceof NumericValue) {
            // known array, known index (a[] = {1,2,3}, a[2] == 3)
            int intIndex = indexValue.value.toInt().value;
            if (intIndex < 0 || intIndex >= arrayValue.values.size()) {
                throw new ArrayIndexOutOfBoundsException();
            }
            builder.setValue(arrayValue.values.get(intIndex));
        } else {
            Value variableValue = builder.currentValue(variableTarget);
            builder.setValue(variableValue);

            if (variableTarget.arrayVariable != null) {
                builder.variableOccursInNotNullContext(variableTarget.arrayVariable, array.value, MultiLevel.EFFECTIVELY_NOT_NULL);
            }
            if (forwardEvaluationInfo.isNotAssignmentTarget()) {
                builder.markRead(variableTarget, evaluationContext.getIteration());
            }
        }

        int notNullRequired = forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL);
        if (notNullRequired > MultiLevel.NULLABLE && builder.getValue() instanceof VariableValue) {
            builder.variableOccursInNotNullContext(((VariableValue) builder.getValue()).variable, builder.getValue(), notNullRequired);
        }
        return builder.build();
    }
}
