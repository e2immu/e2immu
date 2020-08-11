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

import com.google.common.collect.Sets;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.ArrayValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.NotNull;

import java.util.*;

/**
 * We do not override variablesMarkRead(), because both the array and the index will be read in the initial
 * evaluations; so an empty list is what is needed.
 */
public class ArrayAccess implements Expression {

    public final Expression expression;
    public final Expression index;

    public ArrayAccess(@NotNull Expression expression, @NotNull Expression index) {
        this.expression = Objects.requireNonNull(expression);
        this.index = Objects.requireNonNull(index);
    }

    @Override
    public ParameterizedType returnType() {
        return expression.returnType().copyWithOneFewerArrays();
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
    public Set<String> imports() {
        return Sets.union(expression.imports(), index.imports());
    }

    @Override
    public Set<TypeInfo> typesReferenced() {
        return Sets.union(expression.typesReferenced(), index.typesReferenced());
    }

    @Override
    public List<Expression> subExpressions() {
        return List.of(expression, index);
    }

    @Override
    public Optional<Variable> assignmentTarget() {
        return expression.assignmentTarget();
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        Value array = expression.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.NOT_NULL);
        Value indexValue = index.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.NOT_NULL);

        Value value;
        if (array instanceof ArrayValue && indexValue instanceof NumericValue) {
            int intIndex = (indexValue).toInt().value;
            ArrayValue arrayValue = (ArrayValue) array;
            if (intIndex < 0 || intIndex >= arrayValue.values.size()) {
                throw new ArrayIndexOutOfBoundsException();
            }
            value = arrayValue.values.get(intIndex);
        } else {
            Set<Variable> dependencies = new HashSet<>(expression.variables());
            dependencies.addAll(index.variables());
            Variable arrayVariable = expression instanceof VariableValue ? ((VariableValue) expression).variable : null;
            value = evaluationContext.arrayVariableValue(array, indexValue, expression.returnType(), dependencies, arrayVariable);

            if (forwardEvaluationInfo.isNotAssignmentTarget()) {
                evaluationContext.markRead(dependentVariableName(array, indexValue));
            }
        }

        int notNullRequired = forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL);
        if (notNullRequired > MultiLevel.NULLABLE && value instanceof VariableValue) {
            StatementAnalyser.variableOccursInNotNullContext(((VariableValue) value).variable, value, evaluationContext, notNullRequired);
        }
        visitor.visit(this, evaluationContext, value);
        return value;
    }

    @Override
    public List<Variable> variablesInScopeSide() {
        return expression.variables();
    }

    @Override
    public List<Variable> variables() {
        return ListUtil.immutableConcat(expression.variables(), index.variables());
    }

    public static String dependentVariableName(Value array, Value index) {
        return array.toString() + "[" + index.toString() + "]";
    }
}
