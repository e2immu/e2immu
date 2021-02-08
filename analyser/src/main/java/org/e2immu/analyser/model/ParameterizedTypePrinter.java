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

package org.e2immu.analyser.model;

import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;
import java.util.stream.Collectors;

public record ParameterizedTypePrinter(boolean fullyQualified, boolean numericTypeParameters) {

    public static final ParameterizedTypePrinter DISTINGUISHING = new ParameterizedTypePrinter(true, true);
    public static final ParameterizedTypePrinter DEFAULT = new ParameterizedTypePrinter(false, false);
    public static final ParameterizedTypePrinter DETAILED = new ParameterizedTypePrinter(true, false);

    /**
     * It is important not too use the inspection provider too eagerly. During bootstrap of the java.lang classes,
     * there are a lot of interdependencies, and this printer does not have an auto-inspect system.
     *
     * @param inspectionProvider Needed to study the type parameters.
     * @param parameterizedType  to be printed
     * @param varargs            in a context where [] becomes ... ?
     * @param withoutArrays      don't print []
     * @return printed result
     */
    public String print(InspectionProvider inspectionProvider,
                        ParameterizedType parameterizedType,
                        boolean varargs,
                        Diamond diamond,
                        boolean withoutArrays) {
        return print(inspectionProvider, parameterizedType, varargs, diamond, withoutArrays, true, new HashSet<>());
    }

    public String print(InspectionProvider inspectionProvider,
                        ParameterizedType parameterizedType,
                        boolean varargs,
                        Diamond diamond,
                        boolean withoutArrays,
                        boolean keepItSimple,
                        Set<TypeParameter> visitedTypeParameters) {
        StringBuilder sb = new StringBuilder();
        switch (parameterizedType.wildCard) {
            case UNBOUND:
                sb.append("?");
                break;
            case EXTENDS:
                sb.append("? extends ");
                break;
            case SUPER:
                sb.append("? super ");
                break;
            case NONE:
        }
        TypeParameter tp = parameterizedType.typeParameter;
        if (tp != null) {
            if (numericTypeParameters) {
                sb.append(tp.isMethodTypeParameter() ? "M" : "T").append(tp.getIndex());
            } else {
                if (visitedTypeParameters.add(tp)) {
                    sb.append(tp.print(inspectionProvider, visitedTypeParameters));
                } else {
                    sb.append(tp.simpleName());
                }
            }
        } else if (parameterizedType.typeInfo != null) {
            if (parameterizedType.parameters.isEmpty()) {
                sb.append(typeName(parameterizedType.typeInfo, keepItSimple, false));
            } else {
                if (parameterizedType.typeInfo.isPrimaryType() ||
                        inspectionProvider.getTypeInspection(parameterizedType.typeInfo).isStatic()) { // shortcut
                    sb.append(singleType(inspectionProvider, parameterizedType.typeInfo, diamond, keepItSimple, false,
                            parameterizedType.parameters, visitedTypeParameters));
                } else {
                    sb.append(distributeTypeParameters(inspectionProvider, parameterizedType, visitedTypeParameters, diamond, keepItSimple));
                }
            }
        }
        if (!withoutArrays) {
            if (varargs) {
                if (parameterizedType.arrays == 0) {
                    throw new UnsupportedOperationException("Varargs parameterized types must have arrays>0!");
                }
                sb.append("[]".repeat(parameterizedType.arrays - 1)).append("...");
            } else {
                sb.append("[]".repeat(parameterizedType.arrays));
            }
        }
        return sb.toString();
    }

    private String typeName(TypeInfo typeInfo, boolean keepItSimple, boolean forceSimple) {
        if (forceSimple) {
            return typeInfo.simpleName;
        }
        if (fullyQualified) {
            return typeInfo.fullyQualifiedName;
        }
        if (keepItSimple) {
            return typeInfo.simpleName;
        }
        // join up to primary type...
        return typeInfo.fromPrimaryTypeDownwards();
    }

    // if a type is a sub-type, the type parameters may belong to any of the intermediate types
    // we should write them there
    private String distributeTypeParameters(InspectionProvider inspectionProvider,
                                            ParameterizedType parameterizedType,
                                            Set<TypeParameter> visitedTypeParameters,
                                            Diamond diamond,
                                            boolean keepItSimple) {
        TypeInfo typeInfo = parameterizedType.typeInfo;
        assert typeInfo != null;
        List<TypeAndParameters> taps = new LinkedList<>();
        int offset = parameterizedType.parameters.size();
        while (typeInfo != null) {
            TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
            List<ParameterizedType> typesForTypeInfo = new ArrayList<>();
            int numTypeParameters = typeInspection.typeParameters().size();
            offset -= numTypeParameters;
            if (offset < 0) {
                throw new UnsupportedOperationException();
            }
            for (int i = 0; i < numTypeParameters; i++) {
                typesForTypeInfo.add(parameterizedType.parameters.get(offset + i));
            }
            TypeInfo next;
            if (typeInfo.packageNameOrEnclosingType.isRight()) {
                next = typeInfo.packageNameOrEnclosingType.getRight();
            } else {
                next = null;
            }
            taps.add(0, new TypeAndParameters(typeInfo, next == null, typesForTypeInfo));
            typeInfo = next;
        }
        return taps.stream().map(tap -> singleType(inspectionProvider,
                tap.typeInfo, diamond, keepItSimple, !tap.isPrimaryType, tap.typeParameters, visitedTypeParameters))
                .collect(Collectors.joining("."));
    }

    static record TypeAndParameters(TypeInfo typeInfo, boolean isPrimaryType, List<ParameterizedType> typeParameters) {
    }

    private String singleType(InspectionProvider inspectionProvider,
                              TypeInfo typeInfo,
                              Diamond diamond,
                              boolean keepItSimple,
                              boolean forceSimple,
                              List<ParameterizedType> typeParameters,
                              Set<TypeParameter> visitedTypeParameters) {
        StringBuilder sb = new StringBuilder(typeName(typeInfo, keepItSimple, forceSimple));
        if (!typeParameters.isEmpty() && diamond != Diamond.NO) {
            sb.append("<");
            if (diamond == Diamond.SHOW_ALL) {
                sb.append(typeParameters.stream().map(tp -> print(inspectionProvider, tp, false, Diamond.SHOW_ALL, false, false,
                        visitedTypeParameters)).collect(Collectors.joining(", ")));
            }
            sb.append(">");
        }
        return sb.toString();
    }
}
