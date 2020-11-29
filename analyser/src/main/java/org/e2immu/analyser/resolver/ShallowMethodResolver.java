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

package org.e2immu.analyser.resolver;

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;

/**
 * run resolution, but only set the overrides()
 */
public class ShallowMethodResolver {

    public static MethodResolution onlyOverrides(InspectionProvider inspectionProvider, MethodInfo methodInfo) {
        return new MethodResolution(overrides(inspectionProvider, methodInfo), Set.of(), MethodResolution.CallStatus.NOT_RESOLVED,
                false, false);
    }

    /**
     * What does it do: look into my super types, and see if you find a method like the one specified
     * NOTE: it does not look "sideways: methods of the same type but where implicit type conversion can take place
     *
     * @param methodInfo: the method for which we're looking for overrides
     * @return all super methods
     */
    public static Set<MethodInfo> overrides(InspectionProvider inspectionProvider, MethodInfo methodInfo) {
        return ImmutableSet.copyOf(recursiveOverridesCall(inspectionProvider, methodInfo.typeInfo, methodInfo, Map.of()));
    }

    private static Set<MethodInfo> recursiveOverridesCall(InspectionProvider inspectionProvider,
                                                          TypeInfo typeToSearch,
                                                          MethodInfo methodInfo,
                                                          Map<NamedType, ParameterizedType> translationMap) {
        Set<MethodInfo> result = new HashSet<>();
        for (ParameterizedType superType : directSuperTypes(inspectionProvider, typeToSearch)) {
            Map<NamedType, ParameterizedType> translationMapOfSuperType;
            if (superType.parameters.isEmpty()) {
                translationMapOfSuperType = translationMap;
            } else {
                assert superType.typeInfo != null;
                ParameterizedType formalType = superType.typeInfo.asParameterizedType(inspectionProvider);
                translationMapOfSuperType = new HashMap<>(translationMap);
                int index = 0;
                for (ParameterizedType parameter : formalType.parameters) {
                    ParameterizedType concreteParameter = superType.parameters.get(index);
                    translationMapOfSuperType.put(parameter.typeParameter, concreteParameter);
                    index++;
                }
            }
            assert superType.typeInfo != null;
            MethodInfo override = findUniqueMethod(inspectionProvider, superType.typeInfo, methodInfo, translationMapOfSuperType);
            if (override != null) {
                result.add(override);
            }
            if (!Primitives.isJavaLangObject(superType.typeInfo)) {
                result.addAll(recursiveOverridesCall(inspectionProvider, superType.typeInfo, methodInfo, translationMapOfSuperType));
            }
        }
        return result;
    }


    /**
     * Find a method, given a translation map
     *
     * @param target         the method to find (typically from a sub type)
     * @param translationMap from the type parameters of this to the concrete types of the sub-type
     * @return the method of this, if deemed the same
     */
    private static MethodInfo findUniqueMethod(InspectionProvider inspectionProvider,
                                               TypeInfo typeInfo,
                                               MethodInfo target,
                                               Map<NamedType, ParameterizedType> translationMap) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        for (MethodInfo methodInfo : typeInspection.methodsAndConstructors()) {
            if (sameMethod(inspectionProvider, methodInfo, target, translationMap)) {
                return methodInfo;
            }
        }
        return null;
    }

    private static boolean sameMethod(InspectionProvider inspectionProvider,
                                      MethodInfo base,
                                      MethodInfo target,
                                      Map<NamedType, ParameterizedType> translationMap) {
        return base.name.equals(target.name) &&
                sameParameters(inspectionProvider.getMethodInspection(base).getParameters(),
                        inspectionProvider.getMethodInspection(target).getParameters(),
                        translationMap);
    }

    private static boolean sameParameters(List<ParameterInfo> parametersOfMyMethod,
                                          List<ParameterInfo> parametersOfTarget,
                                          Map<NamedType, ParameterizedType> translationMap) {
        if (parametersOfMyMethod.size() != parametersOfTarget.size()) return false;
        int i = 0;
        for (ParameterInfo parameterInfo : parametersOfMyMethod) {
            ParameterInfo p2 = parametersOfTarget.get(i);
            if (differentType(parameterInfo.parameterizedType, p2.parameterizedType, translationMap)) return false;
            i++;
        }
        return true;
    }

    /**
     * This method is NOT the same as <code>isAssignableFrom</code>, and it serves a different purpose.
     * We need to take care to ensure that overloads are different.
     * <p>
     * java.lang.Appendable.append(java.lang.CharSequence) and java.lang.AbstractStringBuilder.append(java.lang.String)
     * can exist together in one class. They are different, even if String is assignable to CharSequence.
     * <p>
     * On the other hand, int comparable(Value other) is the same method as int comparable(T) in Comparable.
     * This is solved by taking the concrete type when we move from concrete types to parameterized types.
     *
     * @param inSuperType    first type
     * @param inSubType      second type
     * @param translationMap a map from type parameters in the super type to (more) concrete types in the sub-type
     * @return true if the types are "different"
     */
    private static boolean differentType(ParameterizedType inSuperType,
                                         ParameterizedType inSubType,
                                         Map<NamedType, ParameterizedType> translationMap) {
        Objects.requireNonNull(inSuperType);
        Objects.requireNonNull(inSubType);
        if (inSuperType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR && inSubType == inSuperType) return false;

        if (inSuperType.typeInfo != null) {
            if (inSubType.typeInfo != inSuperType.typeInfo) return true;
            if (inSuperType.parameters.size() != inSubType.parameters.size()) return true;
            int i = 0;
            for (ParameterizedType param1 : inSuperType.parameters) {
                ParameterizedType param2 = inSubType.parameters.get(i);
                if (differentType(param1, param2, translationMap)) return true;
                i++;
            }
            return false;
        }
        if (inSuperType.typeParameter != null && inSubType.typeInfo != null) {
            // check if we can go from the parameter to the concrete type
            ParameterizedType inMap = translationMap.get(inSuperType.typeParameter);
            if (inMap == null) return true;
            return differentType(inMap, inSubType, translationMap);
        }
        if (inSuperType.typeParameter == null && inSubType.typeParameter == null) return false;
        if (inSuperType.typeParameter == null || inSubType.typeParameter == null) return true;
        // they CAN have different indices, example in BiFunction TestTestExamplesWithAnnotatedAPIs, AnnotationsOnLambdas
        ParameterizedType translated =
                translationMap.get(inSuperType.typeParameter);
        if (translated != null && translated.typeParameter == inSubType.typeParameter) return false;
        if (inSubType.isUnboundParameterType() && inSuperType.isUnboundParameterType()) return false;
        List<ParameterizedType> inSubTypeBounds = inSubType.typeParameter.getTypeBounds();
        List<ParameterizedType> inSuperTypeBounds = inSuperType.typeParameter.getTypeBounds();
        if (inSubTypeBounds.size() != inSuperTypeBounds.size()) return true;
        int i = 0;
        for (ParameterizedType typeBound : inSubType.typeParameter.getTypeBounds()) {
            boolean different = differentType(typeBound, inSuperTypeBounds.get(i), translationMap);
            if (different) return true;
        }
        return false;
    }

    public static List<ParameterizedType> directSuperTypes(InspectionProvider inspectionProvider, TypeInfo typeInfo) {
        if (Primitives.isJavaLangObject(typeInfo)) return List.of();
        List<ParameterizedType> list = new ArrayList<>();
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        list.add(typeInspection.parentClass());
        list.addAll(typeInspection.interfacesImplemented());
        return list;
    }

}
