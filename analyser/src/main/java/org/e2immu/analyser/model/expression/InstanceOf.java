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
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;


@E2Container
public record InstanceOf(Primitives primitives,
                         ParameterizedType parameterizedType,
                         Expression expression,
                         Variable variable,
                         ObjectFlow objectFlow) implements Expression {

    public InstanceOf {
        Objects.requireNonNull(objectFlow);
        Objects.requireNonNull(parameterizedType);
        assert expression != null || variable != null;
        Objects.requireNonNull(primitives);
    }

    public InstanceOf(Primitives primitives, ParameterizedType parameterizedType, Expression expression) {
        this(primitives, parameterizedType, expression, null, ObjectFlow.NO_FLOW);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceOf that = (InstanceOf) o;
        return parameterizedType.equals(that.parameterizedType) &&
                (expression == null ? variable.equals(that.variable) : expression.equals(that.expression));
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, expression, variable);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return UnknownExpression.primitiveGetProperty(variableProperty);
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
        int c = variable.fullyQualifiedName().compareTo(((InstanceOf) v).variable.fullyQualifiedName());
        if (c == 0) c = parameterizedType.detailedString()
                .compareTo(((InstanceOf) v).parameterizedType.detailedString());
        return c;
    }

    @Override
    public OutputBuilder output() {
        return new OutputBuilder().add(expression != null ? expression.output() : variable.output())
                .add(Symbol.INSTANCE_OF).add(parameterizedType.output());
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return evaluationContext.linkedVariables(variable);
    }

    @Override
    public List<Variable> variables() {
        return List.of(variable);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult evaluationResult = expression.evaluate(evaluationContext, forwardEvaluationInfo);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(evaluationResult);
        return localEvaluation(builder, evaluationContext, evaluationResult.getExpression());
    }

    private EvaluationResult localEvaluation(EvaluationResult.Builder builder, EvaluationContext evaluationContext, Expression value) {
        Primitives primitives = evaluationContext.getPrimitives();

        if (value.isUnknown() || value.isDelayed(evaluationContext)) {
            return builder.setExpression(value).build();
        }
        if (value instanceof NullConstant) {
            return builder.setExpression(new BooleanConstant(evaluationContext.getPrimitives(), false)).build();

        }
        if (value instanceof VariableExpression ve) {
            Location location = evaluationContext.getLocation(this);
            ObjectFlow objectFlow = builder.createInternalObjectFlow(location, primitives.booleanParameterizedType, Origin.RESULT_OF_OPERATOR);
            InstanceOf instanceOf = new InstanceOf(primitives, parameterizedType, null, ve.variable(), objectFlow);
            return builder.setExpression(instanceOf).build();
        }
        if (value instanceof NewObject newObject) {
            EvaluationResult er = BooleanConstant.of(parameterizedType.isAssignableFrom(InspectionProvider.defaultFrom(primitives),
                    newObject.parameterizedType()),
                    evaluationContext.getLocation(this), evaluationContext, Origin.RESULT_OF_OPERATOR);
            return builder.compose(er).setExpression(er.value()).build();
        }
        if (value instanceof MethodCall) {
            return builder.setExpression(new UnknownExpression(returnType(), "instanceof value")).build(); // no clue, too deep
        }
        if (value instanceof ClassExpression ce) {
            EvaluationResult er = BooleanConstant.of(parameterizedType.isAssignableFrom(InspectionProvider.defaultFrom(primitives),
                    ce.parameterizedType()), evaluationContext.getLocation(this), evaluationContext, Origin.RESULT_OF_OPERATOR);
            return builder.compose(er).setExpression(er.value()).build();
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
    public Precedence precedence() {
        return Precedence.INSTANCE_OF;
    }

    @Override
    public List<? extends Element> subElements() {
        return expression != null ? List.of(expression) : List.of();
    }

    @Override
    public boolean isDelayed(EvaluationContext evaluationContext) {
        if (variable == null) return false;
        return evaluationContext.variableIsDelayed(variable);
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(expression.typesReferenced(), parameterizedType.typesReferenced(true));
    }
}
