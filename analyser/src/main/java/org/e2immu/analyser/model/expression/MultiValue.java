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

/*
A final field can have been initialised with multiple different values; in some situations
it pays to keep track of all of them.
 */
@E2Container
public class MultiValue implements Expression {

    public final MultiExpression multiExpression;
    public final ObjectFlow objectFlow;
    private final ParameterizedType commonType;
    private final Primitives primitives;

    public MultiValue(Primitives primitives,
                      ObjectFlow objectFlow,
                      MultiExpression multiExpression,
                      ParameterizedType formalCommonType) {
        this.objectFlow = Objects.requireNonNull(objectFlow);
        this.commonType = best(formalCommonType, multiExpression.commonType(primitives));
        this.primitives = primitives;
        this.multiExpression = multiExpression;
    }

    private ParameterizedType best(ParameterizedType formalCommonType, ParameterizedType commonType) {
        return formalCommonType; // IMPROVE
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        List<EvaluationResult> reClauseERs = multiExpression.stream().map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        Expression[] reValues = reClauseERs.stream().map(EvaluationResult::value).toArray(Expression[]::new);
        MultiExpression reMulti = new MultiExpression(reValues);
        return new EvaluationResult.Builder()
                .compose(reClauseERs)
                .setExpression(new MultiValue(evaluationContext.getPrimitives(), objectFlow, reMulti, commonType))
                .build();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new MultiValue(primitives, ObjectFlow.NYE,
                new MultiExpression(multiExpression.stream().map(translationMap::translateExpression)
                        .toArray(Expression[]::new)), translationMap.translateType(commonType));
    }

    @Override
    public ParameterizedType returnType() {
        return commonType;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    public OutputBuilder output(Qualification qualification) {
        // NOT part of standard java, this is an internal construct
        return new OutputBuilder()
                .add(Symbol.LEFT_BRACKET)
                .add(multiExpression.stream().map(expression -> expression.output(qualification))
                        .collect(OutputBuilder.joining(Symbol.COMMA)))
                .add(Symbol.RIGHT_BRACKET);
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
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_ARRAY;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return Arrays.compare(multiExpression.expressions(), ((MultiValue) v).multiExpression.expressions());
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        if (VariableProperty.NOT_NULL_EXPRESSION == variableProperty) {
            int notNull = multiExpression.getProperty(evaluationContext, variableProperty, duringEvaluation);
            if (notNull == Level.DELAY) return Level.DELAY;
            return MultiLevel.shift(MultiLevel.EFFECTIVE, notNull); // default = @NotNull level 0
        }
        // default is to refer to each of the components
        return multiExpression.getProperty(evaluationContext, variableProperty, duringEvaluation);
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
        MultiValue that = (MultiValue) o;
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
    public NewObject getInstance(EvaluationResult evaluationResult) {
        return NewObject.forGetInstance(evaluationResult.evaluationContext().newObjectIdentifier(),
                primitives, returnType(), getObjectFlow());
    }
}
