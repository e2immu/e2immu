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
import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.E2Immutable;

import java.util.List;
import java.util.Objects;

@E2Immutable
public record FieldAccess(Expression expression,
                          Variable variable) implements Expression {
    public FieldAccess(Expression expression, Variable variable) {
        this.variable = Objects.requireNonNull(variable);
        this.expression = Objects.requireNonNull(expression);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldAccess that = (FieldAccess) o;
        return expression.equals(that.expression) &&
                variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, variable);
    }

    public static Expression orElse(Expression expression, Expression alternative) {
        return expression == null ? alternative : expression;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new FieldAccess(translationMap.translateExpression(expression), translationMap.translateVariable(variable));
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public NewObject getInstance(EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NYE;
    }

    @Override
    public boolean hasBeenEvaluated() {
        return false;
    }

    @Override
    public ParameterizedType returnType() {
        return variable.concreteReturnType();
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output() {
        return new OutputBuilder().add(outputInParenthesis(precedence(), expression)).add(Symbol.DOT).add(variable.output());
    }

    @Override
    public int precedence() {
        return 16;
    }


    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }

    @Override
    public List<Variable> variables() {
        return List.of(variable);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult scopeResult = expression.evaluate(evaluationContext, forwardEvaluationInfo.copyModificationEnsureNotNull());
        Variable newVar;
        if (scopeResult.value instanceof VariableExpression variableValue && variable instanceof FieldReference fieldReference) {
            newVar = new FieldReference(evaluationContext.getAnalyserContext(),
                    fieldReference.fieldInfo, variableValue.variable());
        } else {
            newVar = variable;
        }
        EvaluationResult evaluationResult = VariableExpression.evaluate(evaluationContext, forwardEvaluationInfo, newVar);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(scopeResult, evaluationResult);

        if (scopeResult.value instanceof NullConstant) {
            builder.raiseError(Message.NULL_POINTER_EXCEPTION);
        } else if (scopeResult.value != EmptyExpression.NO_VALUE && !evaluationContext.isNotNull0(scopeResult.value)) {
            builder.raiseError(Message.POTENTIAL_NULL_POINTER_EXCEPTION, "Scope " + scopeResult.value);
        }

        return builder.build();
    }
}
