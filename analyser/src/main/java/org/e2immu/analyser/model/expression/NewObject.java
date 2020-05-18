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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.ArrayValue;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class NewObject implements HasParameterExpressions {
    public final ParameterizedType parameterizedType;
    public final List<Expression> parameterExpressions;
    public final TypeInfo anonymousClass;
    public final MethodInfo constructor;
    public final ArrayInitializer arrayInitializer;

    public NewObject(@NotNull MethodInfo constructor,
                     @NotNull ParameterizedType parameterizedType,
                     @NotNull List<Expression> parameterExpressions,
                     ArrayInitializer arrayInitializer) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.constructor = Objects.requireNonNull(constructor);
        this.anonymousClass = null;
        this.arrayInitializer = arrayInitializer;
    }

    public NewObject(@NotNull ParameterizedType parameterizedType, @NotNull TypeInfo anonymousClass) {
        this.anonymousClass = Objects.requireNonNull(anonymousClass);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = List.of();
        this.constructor = null;
        this.arrayInitializer = null;
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
        String arrayInit = arrayInitializer == null ? "" : arrayInitializer.expressionString(0);
        return "new " + parameterizedType.streamWithoutArrays() + expressionString + anon + arrayInit;
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
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        Value value;
        if (arrayInitializer != null) {
            List<Value> values = arrayInitializer.expressions.stream()
                    .map(e -> e.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.DEFAULT))
                    .collect(Collectors.toList());
            value = new ArrayValue(values);
        } else {
            List<Value> parameterValues = transform(parameterExpressions, evaluationContext, visitor, constructor);
            value = new Instance(parameterizedType, constructor, parameterValues);
        }
        visitor.visit(this, evaluationContext, value);
        return value;
    }

    static List<Value> transform(List<Expression> parameterExpressions,
                                 EvaluationContext evaluationContext,
                                 EvaluationVisitor visitor,
                                 MethodInfo methodInfo) {
        List<Value> parameterValues = new ArrayList<>();
        int i = 0;
        for (Expression parameterExpression : parameterExpressions) {
            ForwardEvaluationInfo forward;
            if (methodInfo != null) {
                List<ParameterInfo> params = methodInfo.methodInspection.get().parameters;
                ParameterInfo parameterInfo;
                if (i >= params.size()) {
                    ParameterInfo lastParameter = params.get(params.size() - 1);
                    if (lastParameter.parameterInspection.get().varArgs) {
                        parameterInfo = lastParameter;
                    } else {
                        throw new UnsupportedOperationException("?");
                    }
                } else {
                    parameterInfo = params.get(i);
                }
                // not modified
                int notModified = parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.NOT_MODIFIED);
                int notModifiedValue = notModified == Level.DELAY ? Level.compose(Level.TRUE, 0) :
                        Level.compose(notModified == Level.TRUE ? Level.FALSE : Level.TRUE, 1);
                // not null
                int notNull = parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL);

                forward = ForwardEvaluationInfo.create(notNull, notModifiedValue);
            } else {
                forward = ForwardEvaluationInfo.DEFAULT;
            }
            Value parameterValue = parameterExpression.evaluate(evaluationContext, visitor, forward);
            parameterValues.add(parameterValue);
            i++;
        }
        return parameterValues;
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        SideEffect params = parameterExpressions.stream()
                .map(e -> e.sideEffect(sideEffectContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);

        if (constructor != null) {
            int notModified = constructor.allParametersNotModified();
            if (notModified == Level.TRUE && params.lessThan(SideEffect.SIDE_EFFECT)) {
                return SideEffect.STATIC_ONLY;
            }
            if (notModified == Level.DELAY) return SideEffect.DELAYED;
        }

        return params;
    }
}
