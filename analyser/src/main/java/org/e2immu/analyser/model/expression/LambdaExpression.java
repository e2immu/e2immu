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

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.abstractvalue.TypeValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

@E2Immutable
public class LambdaExpression implements Expression {
    public final Expression expression;
    public final List<ParameterInfo> parameters;
    public final ParameterizedType returnType;
    public final ParameterizedType functionalType;

    public LambdaExpression(List<ParameterInfo> parameters, Expression expression, ParameterizedType returnType, ParameterizedType functionalType) {
        this.expression = Objects.requireNonNull(expression);
        this.parameters = Objects.requireNonNull(parameters);
        this.returnType = Objects.requireNonNull(returnType);
        this.functionalType = Objects.requireNonNull(functionalType);
    }

    @Override
    public Expression translate(Map<? extends Variable, ? extends Variable> translationMap) {
        throw new UnsupportedOperationException("NYI");
    }

    // this is a functional interface
    @Override
    @NotNull
    public ParameterizedType returnType() {
        return returnType;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        String params = parameters.size() == 1 ? parameters.get(0).stream() :
                "(" + parameters.stream().map(ParameterInfo::stream).collect(Collectors.joining(", ")) + ")";
        return params + " -> " + expression.expressionString(indent);
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    @Independent
    @NotNull
    public Set<String> imports() {
        Set<String> imports = new HashSet<>(expression.imports());
        parameters.forEach(pe -> imports.addAll(pe.imports()));
        return ImmutableSet.copyOf(imports);
    }

    @Override
    public Set<TypeInfo> typesReferenced() {
        return SetUtil.immutableUnion(expression.typesReferenced(), parameters.stream().flatMap(p -> p.typesReferenced().stream()).collect(Collectors.toSet()));
    }

    // NOTE: this one is used for finding structures inside the lambda
    @Override
    public List<Expression> subExpressions() {
        return List.of(expression);
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationContext childContext = evaluationContext.child(UnknownValue.EMPTY, null, false);
        parameters.forEach(pi -> childContext.createLocalVariableOrParameter(pi));
        Value v = expression.evaluate(childContext, visitor, forwardEvaluationInfo);
        evaluationContext.merge(childContext);
        visitor.visit(this, evaluationContext, v);

        MethodInfo methodInfo = functionalType.findSingleAbstractMethodOfInterface().methodInfo;
        return new Instance(methodInfo.typeInfo.asParameterizedType(), null, List.of(), evaluationContext);
    }
}
