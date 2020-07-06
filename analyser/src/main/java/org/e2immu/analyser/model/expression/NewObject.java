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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.ArrayValue;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
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
            value = new ArrayValue(new ObjectFlow(evaluationContext.getLocation(), arrayInitializer.commonType.bestTypeInfo()), values);
        } else {
            List<Value> parameterValues = transform(parameterExpressions, evaluationContext, visitor, constructor);
            value = new Instance(parameterizedType, constructor, parameterValues, evaluationContext.getLocation());
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
                // NOT_NULL, NOT_MODIFIED, SIZE
                Map<VariableProperty, Integer> map = parameterInfo.parameterAnalysis.get().getProperties(VariableProperty.FORWARD_PROPERTIES_ON_PARAMETERS);
                if (map.containsValue(Level.DELAY)) {
                    map.put(VariableProperty.METHOD_DELAY, Level.TRUE);
                }
                forward = new ForwardEvaluationInfo(map, false);
            } else {
                forward = ForwardEvaluationInfo.DEFAULT;
            }
            Value parameterValue = parameterExpression.evaluate(evaluationContext, visitor, forward);
            parameterValues.add(parameterValue);
            i++;
        }
        if (methodInfo != null && methodInfo.methodAnalysis.isSet() && methodInfo.methodAnalysis.get().precondition.isSet()) {
            // there is a precondition, and we have a list of values... let's see what we can learn
            // the precondition is using parameter info's as variables so we'll have to substitute
            Value precondition = methodInfo.methodAnalysis.get().precondition.get();
            Map<Value, Value> translationMap = translationMap(evaluationContext, methodInfo, parameterValues);
            Value reEvaluated = precondition.reEvaluate(translationMap);
            // from the result we either may infer another condition, or values to be set...
            Map<Variable, Boolean> individualNullClauses = reEvaluated.individualNullClauses();
            for (Map.Entry<Variable, Boolean> nullClauseEntry : individualNullClauses.entrySet()) {
                if (!nullClauseEntry.getValue()) {
                    evaluationContext.addPropertyRestriction(nullClauseEntry.getKey(), VariableProperty.NOT_NULL, Level.TRUE);
                }
            }
            Value nonIndividual = precondition.nonIndividualCondition();
            if (nonIndividual != null) {
                // TODO we'll need to wrap the method call object in some precondition value
            }
        }
        return parameterValues;
    }

    public static Map<Value, Value> translationMap(EvaluationContext evaluationContext, MethodInfo methodInfo, List<Value> parameters) {
        ImmutableMap.Builder<Value, Value> builder = new ImmutableMap.Builder<>();
        int i = 0;
        for (Value parameterValue : parameters) {
            ParameterInfo parameterInfo = methodInfo.methodInspection.get().parameters.get(i);
            Value vv = new VariableValue(evaluationContext, parameterInfo, parameterInfo.name, null); // TODO ObjectFlow
            builder.put(vv, parameterValue);
            i++;
        }
        return builder.build();
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        SideEffect params = parameterExpressions.stream()
                .map(e -> e.sideEffect(sideEffectContext))
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
