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
import org.e2immu.analyser.model.abstractvalue.ConstrainedNumericValue;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
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

        // not modified on scope
        SideEffect sideEffect = methodInfo.sideEffect();
        boolean safeMethod = sideEffect.lessThan(SideEffect.SIDE_EFFECT);
        int notModifiedValue = sideEffect == SideEffect.DELAYED ? Level.DELAY : safeMethod ? Level.TRUE : Level.FALSE;

        // scope
        Value objectValue = computedScope.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.create(Map.of(
                VariableProperty.NOT_NULL, Level.TRUE,
                VariableProperty.NOT_MODIFIED, notModifiedValue)));

        // null scope
        if (objectValue instanceof NullValue) {
            evaluationContext.raiseError(Message.NULL_POINTER_EXCEPTION);
        }

        // process parameters
        List<Value> parameters = NewObject.transform(parameterExpressions, evaluationContext, visitor, methodInfo);

        // @Size as method annotation
        Value sizeShortCut = computeSize(objectValue, evaluationContext);
        if (sizeShortCut != null) {
            visitor.visit(this, evaluationContext, sizeShortCut);
            return sizeShortCut;
        }

        // @Identity as method annotation
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        Value identity = computeIdentity(methodAnalysis, parameters);
        if (identity != null) {
            visitor.visit(this, evaluationContext, identity);
            return identity;
        }

        // @Fluent as method annotation
        Value fluent = computeFluent(methodAnalysis, objectValue);
        if (fluent != null) {
            visitor.visit(this, evaluationContext, fluent);
            return fluent;
        }

        Value result;
        if (methodAnalysis.singleReturnValue.isSet()) {
            // if this method was identity?
            result = methodAnalysis.singleReturnValue.get();
        } else if (methodInfo.hasBeenDefined()) {
            // we will, at some point, analyse this method
            result = UnknownValue.NO_VALUE;
        } else {
            // we will never analyse this method
            result = new MethodValue(methodInfo, objectValue, parameters);

            // simple example of a frequently recurring issue...
            if (methodInfo.fullyQualifiedName().equals("java.lang.String.toString()")) {
                ParameterizedType type = objectValue.type();
                if (type != null && type.typeInfo != null && type.typeInfo == Primitives.PRIMITIVES.stringTypeInfo) {
                    evaluationContext.raiseError(Message.UNNECESSARY_METHOD_CALL);
                }
            }
        }

        visitor.visit(this, evaluationContext, result);
        return result;
    }

    private Value computeFluent(MethodAnalysis methodAnalysis, Value scope) {
        int fluent = methodAnalysis.getProperty(VariableProperty.FLUENT);
        if (fluent == Level.DELAY && methodAnalysis.hasBeenDefined) return UnknownValue.NO_VALUE;
        if (fluent != Level.TRUE) return null;
        return scope;
    }

    private Value computeIdentity(MethodAnalysis methodAnalysis, List<Value> parameters) {
        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.DELAY && methodAnalysis.hasBeenDefined) return UnknownValue.NO_VALUE; // delay
        if (identity != Level.TRUE) return null;
        return parameters.get(0);
    }

    private Value computeSize(Value objectValue, EvaluationContext evaluationContext) {
        if (!computedScope.returnType().hasSize()) return null; // this type does not do size computations

        int requiredSize = methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE);
        if (requiredSize == Level.DELAY) return null;
        // we have an @Size annotation on the method that we're calling
        int sizeOfObject = objectValue.getProperty(evaluationContext, VariableProperty.SIZE);

        // SITUATION 1: @Size(equals = 0) boolean isEmpty() { }, @Size(min = 1) boolean isNotEmpty() {}, etc.
        if (methodInfo.returnType().isBoolean()) {
            if (sizeOfObject == Level.DELAY) return UnknownValue.NO_VALUE;
            // there is an @Size annotation on a method returning a boolean...
            evaluationContext.raiseError(Message.METHOD_EVALUATES_TO_CONSTANT);
            return Analysis.compatibleSizes(sizeOfObject, requiredSize) ? BoolValue.TRUE : BoolValue.FALSE;
        }

        // SITUATION 2: @Size int size(): this method returns the size
        if (TypeInfo.returnsIntOrLong(methodInfo)) {
            if (sizeOfObject == Level.DELAY) return UnknownValue.NO_VALUE;
            if (Analysis.haveEquals(sizeOfObject)) {
                return new IntValue(Analysis.sizeEquals(sizeOfObject));
            }
            return ConstrainedNumericValue.lowerBound(Primitives.PRIMITIVES.intParameterizedType, Analysis.sizeMin(sizeOfObject), true);
        }
        return null;
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
