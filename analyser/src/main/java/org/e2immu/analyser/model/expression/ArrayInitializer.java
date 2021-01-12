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
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;

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
        List<Expression> reValues = reClauseERs.stream().map(EvaluationResult::value).collect(Collectors.toList());
        return new EvaluationResult.Builder()
                .compose(reClauseERs)
                .setExpression(new ArrayInitializer(evaluationContext.getPrimitives(), objectFlow, reValues))
                .build();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new ArrayInitializer(primitives, ObjectFlow.NYE,
                multiExpression.stream().map(translationMap::translateExpression)
                        .collect(Collectors.toList()));
    }

    @Override
    public ParameterizedType returnType() {
        return commonType;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    public OutputBuilder output() {
        return new OutputBuilder()
                .add(Symbol.LEFT_BRACE)
                .add(multiExpression.stream().map(Element::output).collect(OutputBuilder.joining(Symbol.COMMA)))
                .add(Symbol.RIGHT_BRACE);
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
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
    public NewObject getInstance(EvaluationResult evaluationContext) {
        return NewObject.forGetInstance(primitives, returnType(), getObjectFlow());
    }
}
