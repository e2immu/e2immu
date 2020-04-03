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

package org.e2immu.analyser.model;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;
import org.e2immu.analyser.util.FirstThen;

import java.util.Objects;

// @ContextClass eventually...
@Container
public abstract class ExpressionWithMethodReferenceResolution implements Expression {

    private final FirstThen<MethodInfo, MethodReferenceResolution> methodReferenceResolution;

    protected ExpressionWithMethodReferenceResolution(@NullNotAllowed MethodInfo methodInfo) {
        methodReferenceResolution = new FirstThen<>(Objects.requireNonNull(methodInfo));
    }

    public boolean hasBeenResolved() {
        return methodReferenceResolution.isSet();
    }

    @NotNull
    @NotModified
    public MethodInfo methodInfo() {
        return methodReferenceResolution.isSet() ? methodReferenceResolution.get().methodInfo : methodReferenceResolution.getFirst();
    }

    @Override
    @NotNull
    @NotModified
    public ParameterizedType returnType() {
        return methodReferenceResolution.isSet() ? methodReferenceResolution.get().concreteReturnType :
                methodReferenceResolution.getFirst().returnType();
    }

    public void resolve(@NullNotAllowed MethodReferenceResolution methodReferenceResolution) {
        this.methodReferenceResolution.set(methodReferenceResolution);
    }
}
