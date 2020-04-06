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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.SideEffectContext;

import java.util.Objects;
import java.util.Set;

// cannot be @ContextClass
@Container
public class MethodReference extends ExpressionWithMethodReferenceResolution {

    public MethodReference(@NullNotAllowed MethodInfo methodInfo, ParameterizedType concreteType) {
        super(methodInfo, concreteType);
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        String methodName = methodInfo.isConstructor ? "new" : methodInfo.name;
        return methodInfo.typeInfo.simpleName + "::" + methodName;
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
}
