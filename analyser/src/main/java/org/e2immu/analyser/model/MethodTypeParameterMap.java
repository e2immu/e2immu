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

import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// contains all information to generate concrete types from a method info
public class MethodTypeParameterMap {

    public final MethodInfo methodInfo;
    private final Map<NamedType, ParameterizedType> concreteTypes;

    public MethodTypeParameterMap(MethodInfo methodInfo, Map<NamedType, ParameterizedType> concreteTypes) {
        this.methodInfo = methodInfo;
        this.concreteTypes = ImmutableMap.copyOf(concreteTypes);
    }

    public ParameterizedType getConcreteReturnType() {
        ParameterizedType returnType = methodInfo.methodInspection.get().returnType;
        return apply(returnType);
    }

    public ParameterizedType getConcreteTypeOfParameter(int i) {
        MethodInspection methodInspection = methodInfo.methodInspection.get();
        int n = methodInspection.parameters.size();
        if (i >= n) {
            // varargs
            return apply(methodInspection.parameters.get(n - 1).parameterizedType);
        }
        return apply(methodInspection.parameters.get(i).parameterizedType);
    }

    public MethodTypeParameterMap expand(Map<NamedType, ParameterizedType> mapExpansion) {
        Map<NamedType, ParameterizedType> join = new HashMap<>(concreteTypes);
        join.putAll(mapExpansion);
        return new MethodTypeParameterMap(methodInfo, ImmutableMap.copyOf(join));
    }

    public ParameterizedType applyMap(TypeParameter typeParameter) {
        return concreteTypes.get(typeParameter);
    }

    public ParameterizedType apply(ParameterizedType input) {
        ParameterizedType pt = input;
        while (pt.isTypeParameter() && concreteTypes.containsKey(pt.typeParameter)) {
            ParameterizedType newPt = concreteTypes.get(pt.typeParameter);
            if (newPt.equals(pt)) break;
            pt = newPt;
        }
        if (pt.parameters.isEmpty()) return pt;
        List<ParameterizedType> recursivelyMappedParameters = pt.parameters.stream().map(this::apply).collect(Collectors.toList());
        if (pt.typeInfo == null) {
            throw new UnsupportedOperationException("? in " + this + ", input " + pt + " has no type");
        }
        return new ParameterizedType(pt.typeInfo, recursivelyMappedParameters);
    }

    @Override
    public String toString() {
        return "method " + methodInfo.fullyQualifiedName() + ", map " + concreteTypes;
    }

    public ParameterizedType inferFunctionalType(List<ParameterizedType> types, ParameterizedType inferredReturnType) {
        Objects.requireNonNull(inferredReturnType);
        Objects.requireNonNull(types);
        return new ParameterizedType(methodInfo.typeInfo, methodInfo.typeParametersComputed(types, inferredReturnType));
    }
}
