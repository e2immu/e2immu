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
import org.e2immu.analyser.parser.Primitives;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public record ParameterizedTypeStreamer(boolean fullyQualified, boolean numericTypeParameters) {

    public static final ParameterizedTypeStreamer DISTINGUISHING = new ParameterizedTypeStreamer(true, true);
    public static final ParameterizedTypeStreamer DEFAULT = new ParameterizedTypeStreamer(false, false);
    public static final ParameterizedTypeStreamer DETAILED = new ParameterizedTypeStreamer(true, false);

    public String stream(InspectionProvider inspectionProvider, ParameterizedType parameterizedType, boolean varargs, boolean withoutArrays) {
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
                sb.append(tp.simpleName());
            }
        } else if (parameterizedType.typeInfo != null) {
            if (parameterizedType.parameters.isEmpty()) {
                if (fullyQualified) {
                    sb.append(parameterizedType.typeInfo.fullyQualifiedName);
                } else {
                    sb.append(parameterizedType.typeInfo.simpleName);
                }
            } else {
                TypeInspection typeInspection = inspectionProvider.getTypeInspection(parameterizedType.typeInfo);
                if (typeInspection.isStatic()) { // shortcut
                    sb.append(singleType(inspectionProvider, parameterizedType.typeInfo, true, parameterizedType.parameters));
                } else {
                    sb.append(distributeTypeParameters(inspectionProvider, parameterizedType));
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

    // if a type is a sub-type, the type parameters may belong to any of the intermediate types
    // we should write them there
    private String distributeTypeParameters(InspectionProvider inspectionProvider, ParameterizedType parameterizedType) {
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
            if (typeInspection.packageNameOrEnclosingType().isRight()) {
                next = typeInspection.packageNameOrEnclosingType().getRight();
            } else {
                next = null;
            }
            taps.add(0, new TypeAndParameters(typeInfo, next == null, typesForTypeInfo));
            typeInfo = next;
        }
        return taps.stream().map(tap -> singleType(inspectionProvider,
                tap.typeInfo, tap.isPrimaryType, tap.typeParameters)).collect(Collectors.joining("."));
    }

    static record TypeAndParameters(TypeInfo typeInfo, boolean isPrimaryType, List<ParameterizedType> typeParameters) {
    }

    private String singleType(InspectionProvider inspectionProvider,
                              TypeInfo typeInfo, boolean isPrimaryType, List<ParameterizedType> typeParameters) {
        StringBuilder sb = new StringBuilder();
        if (fullyQualified && isPrimaryType) {
            sb.append(typeInfo.fullyQualifiedName);
        } else {
            sb.append(typeInfo.simpleName);
        }
        if (!typeParameters.isEmpty()) {
            sb.append("<");
            sb.append(typeParameters.stream().map(tp -> stream(inspectionProvider, tp,false, false))
                    .collect(Collectors.joining(", ")));
            sb.append(">");
        }
        return sb.toString();
    }
}
