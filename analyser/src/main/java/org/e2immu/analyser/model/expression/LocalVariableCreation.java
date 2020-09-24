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
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class LocalVariableCreation implements Expression {

    public final LocalVariable localVariable;
    public final LocalVariableReference localVariableReference;
    public final Expression expression;

    public LocalVariableCreation(@NotNull LocalVariable localVariable,
                                 @NotNull Expression expression) {
        this.localVariable = Objects.requireNonNull(localVariable);
        this.expression = Objects.requireNonNull(expression);
        localVariableReference = new LocalVariableReference(localVariable,
                expression == EmptyExpression.EMPTY_EXPRESSION ? List.of() : List.of(expression));
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new LocalVariableCreation(translationMap.translateLocalVariable(localVariable),
                translationMap.translateExpression(expression));
    }

    @Override
    public ParameterizedType returnType() {
        return Primitives.PRIMITIVES.voidParameterizedType;
    }

    @Override
    public String expressionString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(localVariable.annotations.stream().map(ann -> ann.stream() + " ").collect(Collectors.joining()))
                .append(localVariable.modifiers.stream().map(modifier -> modifier.toJava() + " ").collect(Collectors.joining()))
                .append(localVariable.parameterizedType.stream())
                .append(" ")
                .append(localVariable.name);
        if (expression != EmptyExpression.EMPTY_EXPRESSION) {
            sb.append(" = ").append(expression.expressionString(indent));
        }
        return sb.toString();
    }

    @Override
    public int precedence() {
        return 0;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                expression.typesReferenced(),
                localVariable.parameterizedType.typesReferenced(true));
    }

    @Override
    public List<? extends Element> subElements() {
        if (expression == EmptyExpression.EMPTY_EXPRESSION) return List.of();
        return List.of(expression);
    }

    @Override
    public List<LocalVariableReference> newLocalVariables() {
        return List.of(localVariableReference);
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        // the creation itself is local; the assignment references in LocalVariableReference are what matters
        return SideEffect.LOCAL;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        Assignment assignment = new Assignment(new VariableExpression(localVariableReference), expression);
        return assignment.evaluate(evaluationContext, forwardEvaluationInfo);
    }

    @Override
    public List<Variable> variables() {
        return List.of(localVariableReference);
    }
}
