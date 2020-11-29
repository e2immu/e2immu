/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.inspector;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
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

    public final MethodInspection methodInspection;
    @NotNull
    public final Map<NamedType, ParameterizedType> concreteTypes;

    public MethodTypeParameterMap(MethodInspection methodInspection, @NotNull Map<NamedType, ParameterizedType> concreteTypes) {
        this.methodInspection = methodInspection; // can be null, for SAMs
        this.concreteTypes = ImmutableMap.copyOf(concreteTypes);
    }

    public boolean isSingleAbstractMethod() {
        return methodInspection != null;
    }

    public MethodTypeParameterMap copyWithoutMethod() {
        return new MethodTypeParameterMap(null, concreteTypes);
    }

    public ParameterizedType getConcreteReturnType() {
        if (!isSingleAbstractMethod())
            throw new UnsupportedOperationException("Can only be called on a single abstract method");
        ParameterizedType returnType = methodInspection.getReturnType();
        return apply(concreteTypes, returnType);
    }

    public ParameterizedType getConcreteTypeOfParameter(int i) {
        if (!isSingleAbstractMethod())
            throw new UnsupportedOperationException("Can only be called on a single abstract method");
        int n = methodInspection.getParameters().size();
        if (i >= n) {
            // varargs
            return apply(concreteTypes, methodInspection.getParameters().get(n - 1).parameterizedType);
        }
        return apply(concreteTypes, methodInspection.getParameters().get(i).parameterizedType);
    }

    public MethodTypeParameterMap expand(Map<NamedType, ParameterizedType> mapExpansion) {
        Map<NamedType, ParameterizedType> join = new HashMap<>(concreteTypes);
        join.putAll(mapExpansion);
        return new MethodTypeParameterMap(methodInspection, ImmutableMap.copyOf(join));
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
        return (isSingleAbstractMethod() ? ("method " + methodInspection.getFullyQualifiedName()) : "No method") + ", map " + concreteTypes;
    }

    public ParameterizedType inferFunctionalType(InspectionProvider inspectionProvider,
                                                 List<ParameterizedType> types,
                                                 ParameterizedType inferredReturnType) {
        Objects.requireNonNull(inferredReturnType);
        Objects.requireNonNull(types);
        if (!isSingleAbstractMethod())
            throw new UnsupportedOperationException("Can only be called on a single abstract method");

        MethodInfo methodInfo = methodInspection.getMethodInfo();
        List<ParameterizedType> parameters = typeParametersComputed(inspectionProvider, methodInfo, types, inferredReturnType);
        return new ParameterizedType(methodInfo.typeInfo, parameters);
    }


    // given R accept(T t), and types={string}, returnType=string, deduce that R=string, T=string, and we have Function<String, String>
    private static List<ParameterizedType> typeParametersComputed(
            InspectionProvider inspectionProvider,
            MethodInfo methodInfo,
            List<ParameterizedType> types,
            ParameterizedType inferredReturnType) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);
        if (typeInspection.typeParameters().isEmpty()) return List.of();
        MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
        return typeInspection.typeParameters().stream().map(typeParameter -> {
            int cnt = 0;
            for (ParameterInfo parameterInfo : methodInspection.getParameters()) {
                if (parameterInfo.parameterizedType.typeParameter == typeParameter) {
                    return types.get(cnt); // this is one we know!
                }
                cnt++;
            }
            if (methodInspection.getReturnType().typeParameter == typeParameter)
                return inferredReturnType;
            return new ParameterizedType(typeParameter, 0, ParameterizedType.WildCard.NONE);
        }).collect(Collectors.toList());
    }


    public boolean isAssignableFrom(MethodTypeParameterMap other) {
        if (!isSingleAbstractMethod() || !other.isSingleAbstractMethod()) throw new UnsupportedOperationException();
        MethodInfo methodInfo = methodInspection.getMethodInfo();
        MethodInfo otherMethodInfo = other.methodInspection.getMethodInfo();
        if (methodInfo.equals(otherMethodInfo)) return true;
        if (methodInspection.getParameters().size() != other.methodInspection.getParameters().size())
            return false;
        int i = 0;
        for (ParameterInfo pi : methodInspection.getParameters()) {
            ParameterInfo piOther = other.methodInspection.getParameters().get(i);
            i++;
        }
        // TODO
        return Primitives.isVoid(methodInspection.getReturnType()) == Primitives.isVoid(other.methodInspection.getReturnType());
    }

    // used in TypeInfo.convertMethodReferenceIntoLambda
    public MethodInspectionImpl.Builder buildCopy(TypeInfo typeInfo) {
        MethodInspectionImpl.Builder copy = new MethodInspectionImpl.Builder(typeInfo, methodInspection.getMethodInfo().name);
        copy.addModifier(MethodModifier.PUBLIC);

        for (ParameterInfo p : methodInspection.getParameters()) {
            ParameterInspectionImpl.Builder newParameterBuilder = new ParameterInspectionImpl.Builder(
                    getConcreteTypeOfParameter(p.index), p.name, p.index);
            if (p.parameterInspection.get().isVarArgs()) {
                newParameterBuilder.setVarArgs(true);
            }
            copy.addParameter(newParameterBuilder);
        }
        copy.setReturnType(getConcreteReturnType());
        return copy;
    }

    public MethodTypeParameterMap translate(TranslationMap translationMap) {
        return new MethodTypeParameterMap(methodInspection, concreteTypes.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> translationMap.translateType(e.getValue()))));
    }
}