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
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Contains all information to generate concrete types from a method info.
 * Instances of this class are often called "SAM" or "single abstract method", because they
 * represent the method of a functional interface.
 * <p>
 * As an extension, methodInfo can be null. This is because concrete types may have to be propagated
 * through a medium of non-functional interfaces to wards lambdas.
 * <p>
 * Example:
 * Map&lt;String, String&gt; map; map.entrySet().stream().collect(Collectors.toMap(e->, e->e.getValue()))
 * <p>
 * The method collect ends up with a concreteTypes map that maps <code>T -> Entry&lt;String, String&gt;</code>.
 * We need to pass this on "through" toMap (which is NOT a functional interface) towards the parameters of toMap,
 * which are functional interfaces.
 */
//
public class MethodTypeParameterMap {

    public final MethodInfo methodInfo;
    @NotNull
    public final Map<NamedType, ParameterizedType> concreteTypes;

    public MethodTypeParameterMap(MethodInfo methodInfo, @NotNull Map<NamedType, ParameterizedType> concreteTypes) {
        this.methodInfo = methodInfo;
        this.concreteTypes = ImmutableMap.copyOf(concreteTypes);
    }

    public boolean isSingleAbstractMethod() {
        return methodInfo != null;
    }

    public MethodTypeParameterMap copyWithoutMethod() {
        return new MethodTypeParameterMap(null, concreteTypes);
    }

    public ParameterizedType getConcreteReturnType() {
        if (!isSingleAbstractMethod())
            throw new UnsupportedOperationException("Can only be called on a single abstract method");
        ParameterizedType returnType = methodInfo.methodInspection.get().returnType;
        return apply(concreteTypes, returnType);
    }

    public ParameterizedType getConcreteTypeOfParameter(int i) {
        if (!isSingleAbstractMethod())
            throw new UnsupportedOperationException("Can only be called on a single abstract method");
        MethodInspection methodInspection = methodInfo.methodInspection.get();
        int n = methodInspection.parameters.size();
        if (i >= n) {
            // varargs
            return apply(concreteTypes, methodInspection.parameters.get(n - 1).parameterizedType);
        }
        return apply(concreteTypes, methodInspection.parameters.get(i).parameterizedType);
    }

    public MethodTypeParameterMap expand(Map<NamedType, ParameterizedType> mapExpansion) {
        Map<NamedType, ParameterizedType> join = new HashMap<>(concreteTypes);
        join.putAll(mapExpansion);
        return new MethodTypeParameterMap(methodInfo, ImmutableMap.copyOf(join));
    }

    public ParameterizedType applyMap(TypeParameter typeParameter) {
        return concreteTypes.get(typeParameter);
    }

    // also used for fields

    // [Type java.util.function.Function<E, ?>], concrete type java.util.stream.Stream<R>, mapExpansion
    // {R as #0 in java.util.stream.Stream.map(Function<? super T, ? extends R>)=Type java.util.function.Function<E, ? extends R>,
    // T as #0 in java.util.function.Function=Type param E}
    public static ParameterizedType apply(Map<NamedType, ParameterizedType> concreteTypes, ParameterizedType input) {
        ParameterizedType pt = input;
        while (pt.isTypeParameter() && concreteTypes.containsKey(pt.typeParameter)) {
            ParameterizedType newPt = concreteTypes.get(pt.typeParameter);
            if (newPt.equals(pt)) break;
            pt = newPt;
        }
        final ParameterizedType stablePt = pt;
        if (stablePt.parameters.isEmpty()) return stablePt;
        List<ParameterizedType> recursivelyMappedParameters = stablePt.parameters.stream()
                .map(x -> x == stablePt || x == input ? stablePt : apply(concreteTypes, x))
                .collect(Collectors.toList());
        if (stablePt.typeInfo == null) {
            throw new UnsupportedOperationException("? input " + stablePt + " has no type");
        }
        return new ParameterizedType(stablePt.typeInfo, recursivelyMappedParameters);
    }

    @Override
    public String toString() {
        return (isSingleAbstractMethod() ? ("method " + methodInfo.fullyQualifiedName()) : "No method") + ", map " + concreteTypes;
    }

    public ParameterizedType inferFunctionalType(List<ParameterizedType> types, ParameterizedType inferredReturnType) {
        Objects.requireNonNull(inferredReturnType);
        Objects.requireNonNull(types);
        if (!isSingleAbstractMethod())
            throw new UnsupportedOperationException("Can only be called on a single abstract method");

        return new ParameterizedType(methodInfo.typeInfo, methodInfo.typeParametersComputed(types, inferredReturnType));
    }

    public boolean isAssignableFrom(MethodTypeParameterMap other) {
        if (!isSingleAbstractMethod() || !other.isSingleAbstractMethod()) throw new UnsupportedOperationException();
        if (methodInfo.equals(other.methodInfo)) return true;
        MethodInspection mi = methodInfo.methodInspection.get();
        MethodInspection miOther = other.methodInfo.methodInspection.get();
        if (mi.parameters.size() != miOther.parameters.size()) return false;
        int i = 0;
        for (ParameterInfo pi : methodInfo.methodInspection.get().parameters) {
            ParameterInfo piOther = other.methodInfo.methodInspection.get().parameters.get(i);
            i++;
        }
        // TODO
        return mi.returnType.isVoid() == miOther.returnType.isVoid();
    }

    public MethodInfo buildCopy(TypeContext typeContext, TypeInfo typeInfo) {
        MethodInfo copy = new MethodInfo(typeInfo, methodInfo.name, false);
        MethodInspection.MethodInspectionBuilder mib = new MethodInspection.MethodInspectionBuilder();
        MethodInspection mi = methodInfo.methodInspection.get();
        mib.addModifier(MethodModifier.PUBLIC);

        for (ParameterInfo p : mi.parameters) {
            ParameterInfo newParameter = new ParameterInfo(
                    typeContext, copy,
                    getConcreteTypeOfParameter(p.index), p.name, p.index);
            mib.addParameter(newParameter);
            ParameterInspection.ParameterInspectionBuilder pib = new ParameterInspection.ParameterInspectionBuilder();
            if (p.parameterInspection.get().varArgs) {
                pib.setVarArgs(true);
            }
            newParameter.parameterInspection.set(pib.build(copy));
        }
        mib.setReturnType(getConcreteReturnType());

        copy.methodInspection.set(mib.build(copy));
        return copy;
    }
}
