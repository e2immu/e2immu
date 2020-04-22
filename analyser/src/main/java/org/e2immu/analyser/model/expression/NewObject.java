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
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class NewObject implements HasParameterExpressions {
    public final ParameterizedType parameterizedType;
    public final List<Expression> parameterExpressions;
    public final TypeInfo anonymousClass;
    public final MethodInfo constructor;

    public NewObject(@NotNull MethodInfo constructor,
                     @NotNull ParameterizedType parameterizedType,
                     @NotNull List<Expression> parameterExpressions) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.constructor = Objects.requireNonNull(constructor);
        this.anonymousClass = null;
    }

    public NewObject(@NotNull ParameterizedType parameterizedType, @NotNull TypeInfo anonymousClass) {
        this.anonymousClass = Objects.requireNonNull(anonymousClass);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = List.of();
        this.constructor = null;
    }

    @Override
    public MethodInfo getMethodInfo() {
        return constructor;
    }

    @Override
    public List<Expression> getParameterExpressions() {
        return parameterExpressions;
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public String expressionString(int indent) {
        String expressionString;
        if (parameterizedType.arrays > 0) {
            expressionString = parameterExpressions.stream().map(expression -> "[" + expression.expressionString(indent) + "]")
                    .collect(Collectors.joining(", "));
        } else {
            expressionString = "(" +
                    parameterExpressions.stream().map(expression -> expression.expressionString(indent)).collect(Collectors.joining(", ")) +
                    ")";
        }
        String anon = (anonymousClass == null ? "" : anonymousClass.stream(indent, false)).stripTrailing();
        return "new " + parameterizedType.stream() + expressionString + anon;
    }

    @Override
    public int precedence() {
        return 13;
    }

    @Override
    public Set<String> imports() {
        Set<String> imports = new HashSet<>();
        if (parameterizedType.typeInfo != null) imports.add(parameterizedType.typeInfo.fullyQualifiedName);
        parameterExpressions.forEach(pe -> imports.addAll(pe.imports()));
        return ImmutableSet.copyOf(imports);
    }

    @Override
    public List<Expression> subExpressions() {
        return parameterExpressions;
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor) {
        List<Value> parameterValues = parameterExpressions.stream()
                .map(pe -> pe.evaluate(evaluationContext, visitor))
                .collect(Collectors.toList());
        Value value = new Instance(parameterizedType, constructor, parameterValues);
        visitor.visit(this, evaluationContext, value);
        return value;
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        SideEffect params = parameterExpressions.stream()
                .map(e -> e.sideEffect(sideEffectContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);

        if (constructor != null) {
            Boolean isNotModified = constructor.isAllParametersNotModified(sideEffectContext.typeContext);
            if (isNotModified == Boolean.TRUE && params.lessThan(SideEffect.SIDE_EFFECT)) {
                return SideEffect.STATIC_ONLY;
            }
            if (isNotModified == null) return SideEffect.DELAYED;
        }

        return params;
    }
}
