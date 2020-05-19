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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.value.ErrorValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class MethodCall extends ExpressionWithMethodReferenceResolution implements HasParameterExpressions {
    public final Expression object;
    public final Expression computedScope;
    public final List<Expression> parameterExpressions;

    public MethodCall(@NotNull Expression object,
                      @NotNull Expression computedScope,
                      @NotNull MethodTypeParameterMap methodTypeParameterMap,
                      @NotNull List<Expression> parameterExpressions) {
        super(methodTypeParameterMap.methodInfo, methodTypeParameterMap.getConcreteReturnType());
        this.object = object;
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.computedScope = Objects.requireNonNull(computedScope);
    }

    @Override
    public List<Expression> getParameterExpressions() {
        return parameterExpressions;
    }

    @Override
    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        StatementAnalyser.checkForIllegalMethodUsageIntoNestedOrEnclosingType(methodInfo, evaluationContext);

        // not modified
        SideEffect sideEffect = methodInfo.sideEffect();
        boolean safeMethod = sideEffect.lessThan(SideEffect.SIDE_EFFECT);
        int notModifiedValue;
        if (sideEffect == SideEffect.DELAYED) {
            notModifiedValue = Level.compose(Level.TRUE, 0);
        } else {
            notModifiedValue = Level.compose(safeMethod ? Level.FALSE : Level.TRUE, 1);
        }
        Value objectValue = computedScope.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.create(Level.TRUE, notModifiedValue));

        Value result;
        if (objectValue instanceof NullValue) {
            result = ErrorValue.nullPointerException(UnknownValue.UNKNOWN_VALUE);
        } else {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();

            List<Value> parameters = NewObject.transform(parameterExpressions, evaluationContext, visitor, methodInfo);

            if (methodAnalysis.singleReturnValue.isSet()) {
                Value singleValue = methodAnalysis.singleReturnValue.get();
                if (!(singleValue instanceof UnknownValue) && methodInfo.cannotBeOverridden()) {
                    result = singleValue;
                } else {
                    result = new MethodValue(methodInfo, objectValue, parameters);
                }
            } else if (methodInfo.hasBeenDefined()) {
                // we will, at some point, analyse this method
                result = UnknownValue.NO_VALUE;
            } else {
                // method has NOT been defined, so we definitely do NOT delay
                int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
                if (identity == Level.TRUE) {
                    result = parameters.get(0);
                } else {
                    // we will never analyse this method
                    result = new MethodValue(methodInfo, objectValue, parameters);

                    // simple example of a frequently recurring issue...
                    if (methodInfo.fullyQualifiedName().equals("java.lang.String.toString()")) {
                        ParameterizedType type = objectValue.type();
                        if (type != null && type.typeInfo != null && "java.lang.String".equals(type.typeInfo.fullyQualifiedName)) {
                            result = ErrorValue.unnecessaryMethodCall(result);
                        }
                    }
                }
            }
        }
        visitor.visit(this, evaluationContext, result);
        return evaluationContext.checkError(result);
    }

    @Override
    public String expressionString(int indent) {
        String scope = "";
        if (object != null) {
            scope = bracketedExpressionString(indent, object) + ".";
        }
        return scope + methodInfo.name +
                "(" + parameterExpressions.stream().map(expression -> expression.expressionString(indent))
                .collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    public Set<String> imports() {
        Set<String> imports = new HashSet<>(object == null ? Set.of() : object.imports());
        parameterExpressions.forEach(pe -> imports.addAll(pe.imports()));
        return ImmutableSet.copyOf(imports);
    }

    @Override
    public List<Expression> subExpressions() {
        List<Expression> list = new ArrayList<>(parameterExpressions);
        if (object != null) {
            list.add(object);
        }
        return ImmutableList.copyOf(list);
    }

    @Override
    public List<Variable> variablesInScopeSide() {
        return object == null ? List.of() : object.variables();
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        Objects.requireNonNull(sideEffectContext);

        SideEffect params = parameterExpressions.stream()
                .map(e -> e.sideEffect(sideEffectContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);

        // look at the object... if it is static, we're in the same boat
        if (object instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) object;
            if (fieldAccess.variable.isStatic() && params.lessThan(SideEffect.SIDE_EFFECT))
                return SideEffect.STATIC_ONLY;
        }
        if (object instanceof VariableExpression) {
            VariableExpression variableExpression = (VariableExpression) object;
            if (variableExpression.variable.isStatic() && params.lessThan(SideEffect.SIDE_EFFECT))
                return SideEffect.STATIC_ONLY;
        }
        if (object != null) {
            SideEffect sideEffect = object.sideEffect(sideEffectContext);
            if (sideEffect == SideEffect.STATIC_ONLY && params.lessThan(SideEffect.SIDE_EFFECT)) {
                return SideEffect.STATIC_ONLY;
            }
        }

        SideEffect methodsSideEffect = methodInfo.sideEffect();
        if (methodsSideEffect == SideEffect.STATIC_ONLY && params.lessThan(SideEffect.SIDE_EFFECT)) {
            return SideEffect.STATIC_ONLY;
        }
        return methodsSideEffect.combine(params);
    }
}
