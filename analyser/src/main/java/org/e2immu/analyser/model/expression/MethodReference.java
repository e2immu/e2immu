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
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.value.ErrorValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;

import java.util.List;
import java.util.Objects;
import java.util.Set;

// cannot be @ContextClass
@Container
public class MethodReference extends ExpressionWithMethodReferenceResolution {

    // either "this", a variable, or a type
    public final Expression scope;

    public MethodReference(Expression scope, @NullNotAllowed MethodInfo methodInfo, ParameterizedType concreteType) {
        super(methodInfo, concreteType);
        this.scope = scope;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        String methodName = methodInfo.isConstructor ? "new" : methodInfo.name;
        return scope.expressionString(0) + "::" + methodName;
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    @NotNull
    @Independent
    public Set<String> imports() {
        return methodInfo.returnType().typeInfo != null ?
                Set.of(methodInfo.returnType().typeInfo.fullyQualifiedName)
                : Set.of();
    }

    // if we pass on one of our own methods to some other method, we need to take into account our exposure to the
    // outside world...
    @Override
    @NotNull
    public SideEffect sideEffect(@NullNotAllowed SideEffectContext sideEffectContext) {
        Objects.requireNonNull(sideEffectContext);
        // we know the method we're passing on...
        if (sideEffectContext.enclosingType.inTypeInnerOuterHierarchy(methodInfo.typeInfo).isPresent()) {
            return sideEffectContext.exposureToOutsideWorld;
        }
        // no idea which method we're passing on... should not be a problem
        return SideEffect.LOCAL;
    }

    @Override
    public List<Variable> variablesInScopeSide() {
        return scope.variables();
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor) {
        Value value = scope.evaluate(evaluationContext, visitor);
        Value result;
        if (value instanceof NullValue) {
            result = ErrorValue.NULL_POINTER_EXCEPTION;
        } else {
            if (methodInfo.methodAnalysis.singleReturnValue.isSet()) {
                Value singleValue = methodInfo.methodAnalysis.singleReturnValue.get();
                if (!(singleValue instanceof UnknownValue) && methodInfo.cannotBeOverridden()) {
                    result = singleValue;
                } else {
                    // TODO we have no idea about the parameters here
                    result = new MethodValue(methodInfo, value, List.of());
                }
            } else {
                result = UnknownValue.NO_VALUE;
            }
        }
        visitor.visit(this, evaluationContext, result);
        return result;
    }
}
