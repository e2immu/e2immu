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
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.value.ArrayValue;
import org.e2immu.analyser.model.value.Instance;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@E2Container
public class ArrayInitializer implements Expression {

    public final MultiExpression multiExpression;
    public final ObjectFlow objectFlow;
    private final ParameterizedType commonType;
    private final Primitives primitives;

    public ArrayInitializer(Primitives primitives, ObjectFlow objectFlow, List<Expression> values) {
        this.objectFlow = Objects.requireNonNull(objectFlow);
        this.multiExpression = MultiExpression.create(values);
        this.commonType = multiExpression.commonType(primitives);
        this.primitives = primitives;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        List<EvaluationResult> reClauseERs = multiExpression.stream().map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        List<Expression> reValues = reClauseERs.stream().map(er -> er.value).collect(Collectors.toList());
        return new EvaluationResult.Builder()
                .compose(reClauseERs)
                .setExpression(new ArrayInitializer(evaluationContext.getPrimitives(), objectFlow, reValues))
                .build();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new ArrayInitializer(primitives, ObjectFlow.NO_FLOW,
                multiExpression.stream().map(translationMap::translateExpression)
                .collect(Collectors.toList()));
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return "{" + multiExpression.stream().map(v -> v.print(printMode)).collect(Collectors.joining(",")) + "}";
    }

    @Override
    public ParameterizedType returnType() {
        return commonType;
    }

    @Override
    public String expressionString(int indent) {
        return multiExpression.stream().map(e -> e.expressionString(indent)).collect(Collectors.joining(", ", "{", "}"));

    }

    @Override
    public int precedence() {
        return 1;
    }


    @Override
    public List<? extends Element> subElements() {
        return List.of(multiExpression.expressions());
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext,
                                     ForwardEvaluationInfo forwardEvaluationInfo) {
        List<EvaluationResult> results = multiExpression.stream()
                .map(e -> e.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT))
                .collect(Collectors.toList());
        List<Expression> values = results.stream().map(EvaluationResult::getExpression).collect(Collectors.toList());

        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(results);
        ObjectFlow objectFlow = builder.createLiteralObjectFlow(commonType);
        builder.setExpression(new ArrayInitializer(evaluationContext.getPrimitives(), objectFlow, values));

        return builder.build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_ARRAY;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return Arrays.compare(multiExpression.expressions(), ((ArrayInitializer) v).multiExpression.expressions());
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) {
            int notNull = multiExpression.getProperty(evaluationContext, variableProperty);
            return MultiLevel.shift(MultiLevel.EFFECTIVE, notNull); // default = @NotNull level 0
        }
        // default is to refer to each of the components
        return multiExpression.getProperty(evaluationContext, variableProperty);
    }

    @Override
    public List<Variable> variables() {
        return multiExpression.variables();
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayInitializer that = (ArrayInitializer) o;
        return Arrays.equals(multiExpression.expressions(), that.multiExpression.expressions());
    }

    @Override
    public int hashCode() {
        return Objects.hash((Object[]) multiExpression.expressions());
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            multiExpression.stream().forEach(v -> v.visit(predicate));
        }
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        return new Instance(type(), getObjectFlow(), EmptyExpression.EMPTY_EXPRESSION);
    }
}
