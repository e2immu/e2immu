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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.value.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;


@E2Container
public record InstanceOf(Primitives primitives,
                         ParameterizedType parameterizedType,
                         Expression expression,
                         Variable variable,
                         ObjectFlow objectFlow) implements Expression {

    public InstanceOf(Primitives primitives, ParameterizedType parameterizedType, Expression expression) {
        this(primitives, parameterizedType, expression, null, ObjectFlow.NO_FLOW);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceOf that = (InstanceOf) o;
        return parameterizedType.equals(that.parameterizedType) &&
                expression == null ? variable.equals(that.variable) : expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, expression, variable);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new InstanceOf(primitives,
                translationMap.translateType(parameterizedType),
                translationMap.translateExpression(expression));
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INSTANCE_OF;
    }

    @Override
    public int internalCompareTo(Expression v) {
        int c = variable.fullyQualifiedName().compareTo(((InstanceOfValue) v).variable.fullyQualifiedName());
        if (c == 0) c = parameterizedType.detailedString()
                .compareTo(((InstanceOfValue) v).parameterizedType.detailedString());
        return c;
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return variable.print(printMode) + " instanceof " + parameterizedType.print(printMode);
    }

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        return evaluationContext.linkedVariables(variable);
    }

    @Override
    public ParameterizedType type() {
        return primitives.booleanParameterizedType;
    }

    @Override
    public List<Variable> variables() {
        return List.of(variable);
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return null;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult evaluationResult = expression.evaluate(evaluationContext, forwardEvaluationInfo);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(evaluationResult);
        return localEvaluation(builder, evaluationContext, evaluationResult.getExpression());
    }

    private EvaluationResult localEvaluation(EvaluationResult.Builder builder, EvaluationContext evaluationContext, Expression value) {
        Primitives primitives = evaluationContext.getPrimitives();

        if (value.isUnknown()) {
            return builder.setExpression(PrimitiveExpression.PRIMITIVE_EXPRESSION).build();
        }
        if (value instanceof NullValue) {
            return builder.setExpression(new BooleanConstant(evaluationContext.getPrimitives(), false)).build();

        }
        if (value instanceof VariableExpression ve) {
            Location location = evaluationContext.getLocation(this);
            ObjectFlow objectFlow = builder.createInternalObjectFlow(location, primitives.booleanParameterizedType, Origin.RESULT_OF_OPERATOR);
            return builder.setExpression(new InstanceOf(primitives, parameterizedType, null, ve.variable(), objectFlow)).build();
        }
        if (value instanceof Instance) {
            EvaluationResult er = BoolValue.of(parameterizedType.isAssignableFrom(InspectionProvider.defaultFrom(primitives),
                    ((Instance) value).parameterizedType),
                    evaluationContext.getLocation(this), evaluationContext, Origin.RESULT_OF_OPERATOR);
            return builder.compose(er).setExpression(er.value).build();
        }
        if (value instanceof MethodValue) {
            return builder.setExpression(PrimitiveExpression.PRIMITIVE_EXPRESSION).build(); // no clue, too deep
        }
        if (value instanceof ClassExpression ce) {
            EvaluationResult er = BooleanConstant.of(parameterizedType.isAssignableFrom(InspectionProvider.defaultFrom(primitives),
                    ce.parameterizedType()), evaluationContext.getLocation(this), evaluationContext, Origin.RESULT_OF_OPERATOR);
            return builder.compose(er).setExpression(er.value).build();
        }
        // this error occurs with a TypeExpression, probably due to our code giving priority to types rather than
        // variable names, when you use a type name as a variable name, which is perfectly allowed in Java but is
        // horrible practice. We leave the bug for now.
        throw new UnsupportedOperationException("? have expression of " + expression.getClass() + " value is " + value + " of " + value.getClass());
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return primitives.booleanParameterizedType;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return bracketedExpressionString(indent, expression) + " instanceof " + parameterizedType.print();
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
