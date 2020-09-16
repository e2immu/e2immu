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
import org.e2immu.analyser.model.abstractvalue.ArrayValue;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;
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
    public Expression translate(TranslationMap translationMap) {
        return new NewObject(constructor,
                translationMap.translateType(parameterizedType),
                parameterExpressions.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                TranslationMap.ensureExpressionType(arrayInitializer, ArrayInitializer.class));
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
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                parameterizedType.typesReferenced(true),
                parameterExpressions.stream().flatMap(e -> e.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()));
    }

    @Override
    public List<? extends Element> subElements() {
        return parameterExpressions;
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        Value value;
        if (arrayInitializer != null) {
            List<Value> values = arrayInitializer.expressions.stream()
                    .map(e -> e.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.DEFAULT))
                    .collect(Collectors.toList());
            value = new ArrayValue(evaluationContext.createLiteralObjectFlow(arrayInitializer.commonType), values);
        } else {
            List<Value> parameterValues = EvaluateParameters.transform(parameterExpressions,
                    evaluationContext, visitor, constructor, Level.FALSE, null);
            value = new Instance(parameterizedType, constructor, parameterValues, evaluationContext);
        }
        visitor.visit(this, evaluationContext, value);
        return value;
    }


    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        SideEffect params = parameterExpressions.stream()
                .map(e -> e.sideEffect(evaluationContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);

        if (constructor != null) {
            int modified = constructor.atLeastOneParameterModified();
            if (modified == Level.FALSE && params.lessThan(SideEffect.SIDE_EFFECT)) {
                return SideEffect.STATIC_ONLY;
            }
            if (modified == Level.DELAY) return SideEffect.DELAYED;
        }

        return params;
    }
}
