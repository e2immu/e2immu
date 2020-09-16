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

import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * groups: FieldInfo, ParameterInfo, LocalVariable
 */

// at some point: @E2Container
public interface Variable {

    static String detailedString(Set<Variable> dependencies) {
        if (dependencies == null) return "";
        return dependencies.stream().map(Variable::detailedString).collect(Collectors.joining("; "));
    }

    ParameterizedType concreteReturnType();

    ParameterizedType parameterizedType();

    String name();

    String detailedString();

    boolean isStatic();

    SideEffect sideEffect(EvaluationContext evaluationContext);

    int variableOrder();

    default UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        return parameterizedType().typesReferenced(explicit);
    }
}
