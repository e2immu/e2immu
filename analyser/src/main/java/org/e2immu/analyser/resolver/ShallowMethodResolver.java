/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.resolver;

import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;

/**
 * run resolution, but only set the overrides()
 */
public class ShallowMethodResolver {

    public static MethodResolution onlyOverrides(InspectionProvider inspectionProvider, MethodInfo methodInfo) {
        return new MethodResolution(overrides(inspectionProvider, methodInfo), Set.of(), MethodResolution.CallStatus.NOT_RESOLVED,
                false, false, true, false);
    }

    /**
     * What does it do: look into my super types, and see if you find a method like the one specified
     * NOTE: it does not look "sideways: methods of the same type but where implicit type conversion can take place
     *
     * @param methodInfo: the method for which we're looking for overrides
     * @return all super methods
     */
    public static Set<MethodInfo> overrides(InspectionProvider inspectionProvider, MethodInfo methodInfo) {
        return Set.copyOf(recursiveOverridesCall(inspectionProvider,
                methodInfo.typeInfo,
                inspectionProvider.getMethodInspection(methodInfo),
                Map.of()));
    }

    // very similar code in TypeInspection.computeIsFunctionalInterface
    private static Set<MethodInfo> recursiveOverridesCall(InspectionProvider inspectionProvider,
                                                          TypeInfo typeToSearch,
                                                          MethodInspection methodInspection,
                                                          Map<NamedType, ParameterizedType> translationMap) {
        Set<MethodInfo> result = new HashSet<>();
        for (ParameterizedType superType : directSuperTypes(inspectionProvider, typeToSearch)) {
            Map<NamedType, ParameterizedType> translationMapOfSuperType = mapOfSuperType(superType, inspectionProvider);
            translationMapOfSuperType.putAll(translationMap);
            assert superType.typeInfo != null;
            MethodInfo override = findUniqueMethod(inspectionProvider, superType.typeInfo, methodInspection, translationMapOfSuperType);
            if (override != null) {
                result.add(override);
            }
            if (!superType.typeInfo.isJavaLangObject()) {
                result.addAll(recursiveOverridesCall(inspectionProvider, superType.typeInfo, methodInspection, translationMapOfSuperType));
            }
        }
        return result;
    }

    public static Map<NamedType, ParameterizedType> mapOfSuperType(ParameterizedType superType, InspectionProvider inspectionProvider) {
        Map<NamedType, ParameterizedType> translationMapOfSuperType = new HashMap<>();
        if (!superType.parameters.isEmpty()) {
            assert superType.typeInfo != null;
            ParameterizedType formalType = superType.typeInfo.asParameterizedType(inspectionProvider);
            int index = 0;
            for (ParameterizedType parameter : formalType.parameters) {
                ParameterizedType concreteParameter = superType.parameters.get(index);
                translationMapOfSuperType.put(parameter.typeParameter, concreteParameter);
                index++;
            }
        }
        return translationMapOfSuperType;
    }


    /**
     * Find a method, given a translation map
     *
     * @param targetInspection the method to find (typically from a sub type)
     * @param translationMap   from the type parameters of this to the concrete types of the sub-type
     * @return the method of this, if deemed the same
     */
    private static MethodInfo findUniqueMethod(InspectionProvider inspectionProvider,
                                               TypeInfo typeInfo,
                                               MethodInspection targetInspection,
                                               Map<NamedType, ParameterizedType> translationMap) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        for (MethodInfo methodInfo : typeInspection.methodsAndConstructors()) {
            if (sameMethod(inspectionProvider, methodInfo, targetInspection, translationMap)) {
                return methodInfo;
            }
        }
        return null;
    }

    private static boolean sameMethod(InspectionProvider inspectionProvider,
                                      MethodInfo base,
                                      MethodInspection targetInspection,
                                      Map<NamedType, ParameterizedType> translationMap) {
        if (!base.name.equals(targetInspection.getMethodInfo().name)) return false;
        MethodInspection baseInspection = inspectionProvider.getMethodInspection(base);
        return sameParameters(inspectionProvider,
                baseInspection.getParameters(), targetInspection.getParameters(), translationMap);
    }

    public static boolean sameParameters(
            InspectionProvider inspectionProvider,
            List<ParameterInfo> parametersOfMyMethod,
            List<ParameterInfo> parametersOfTarget,
            Map<NamedType, ParameterizedType> translationMap) {
        if (parametersOfMyMethod.size() != parametersOfTarget.size()) return false;
        int i = 0;
        for (ParameterInfo parameterInfo : parametersOfMyMethod) {
            ParameterInfo p2 = parametersOfTarget.get(i);
            if (differentType(inspectionProvider, parameterInfo.parameterizedType, p2.parameterizedType, translationMap)) {
                return false;
            }
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
    private static boolean differentType(
            InspectionProvider inspectionProvider,
            ParameterizedType inSuperType,
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
                if (differentType(inspectionProvider, param1, param2, translationMap)) return true;
                i++;
            }
            return false;
        }
        if (inSuperType.typeParameter != null && inSubType.typeInfo != null) {
            // check if we can go from the parameter to the concrete type
            ParameterizedType inMap = translationMap.get(inSuperType.typeParameter);
            if (inMap == null) return true;
            if (inMap.typeParameter != inSuperType.typeParameter) {
                return differentType(inspectionProvider, inMap, inSubType, translationMap);
            } // else: the map doesn't point us to some other place
        }
        if (inSuperType.typeParameter == null && inSubType.typeParameter == null) return false;
        if (inSuperType.typeParameter == null || inSubType.typeParameter == null) return true;
        // they CAN have different indices, example in BiFunction TestTestExamplesWithAnnotatedAPIs, AnnotationsOnLambdas
        ParameterizedType translated =
                translationMap.get(inSuperType.typeParameter);
        if (translated != null && translated.typeParameter == inSubType.typeParameter) return false;
        if (inSubType.isUnboundTypeParameter(inspectionProvider) &&
                inSuperType.isUnboundTypeParameter(inspectionProvider)) return false;
        List<ParameterizedType> inSubTypeBounds = inSubType.typeParameter.getTypeBounds();
        List<ParameterizedType> inSuperTypeBounds = inSuperType.typeParameter.getTypeBounds();
        if (inSubTypeBounds.size() != inSuperTypeBounds.size()) return true;
        int i = 0;
        for (ParameterizedType typeBound : inSubType.typeParameter.getTypeBounds()) {
            boolean different = differentType(inspectionProvider, typeBound, inSuperTypeBounds.get(i), translationMap);
            if (different) return true;
        }
        return false;
    }

    public static List<ParameterizedType> directSuperTypes(InspectionProvider inspectionProvider, TypeInfo typeInfo) {
        if (typeInfo.isJavaLangObject()) return List.of();
        List<ParameterizedType> list = new ArrayList<>();
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        list.add(typeInspection.parentClass());
        list.addAll(typeInspection.interfacesImplemented());
        return list;
    }

}
