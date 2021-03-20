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

package org.e2immu.analyser.model;

import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;

public class ParameterizedTypePrinter {

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
    public static OutputBuilder print(InspectionProvider inspectionProvider,
                                      Qualification qualification,
                                      ParameterizedType parameterizedType,
                                      boolean varargs,
                                      Diamond diamond,
                                      boolean withoutArrays) {
        return print(inspectionProvider, qualification, parameterizedType, varargs, diamond, withoutArrays, new HashSet<>());
    }

    public static OutputBuilder print(InspectionProvider inspectionProvider,
                                      Qualification qualification,
                                      ParameterizedType parameterizedType,
                                      boolean varargs,
                                      Diamond diamond,
                                      boolean withoutArrays,
                                      Set<TypeParameter> visitedTypeParameters) {
        OutputBuilder outputBuilder = new OutputBuilder();
        switch (parameterizedType.wildCard) {
            case UNBOUND:
                outputBuilder.add(new Text("?"));
                break;
            case EXTENDS:
                outputBuilder.add(new Text("?")).add(Space.ONE).add(new Text("extends")).add(Space.ONE);
                break;
            case SUPER:
                outputBuilder.add(new Text("?")).add(Space.ONE).add(new Text("super")).add(Space.ONE);
                break;
            case NONE:
        }
        TypeParameter tp = parameterizedType.typeParameter;
        if (tp != null) {
            if (qualification.useNumericTypeParameters()) {
                outputBuilder.add(new Text((tp.isMethodTypeParameter() ? "M" : "T") + tp.getIndex()));
            } else {
                if (visitedTypeParameters.add(tp)) {
                    outputBuilder.add(tp.output(inspectionProvider, qualification, visitedTypeParameters));
                } else {
                    outputBuilder.add(new Text(tp.simpleName()));
                }
            }
        } else if (parameterizedType.typeInfo != null) {
            if (parameterizedType.parameters.isEmpty()) {
                outputBuilder.add(new TypeName(parameterizedType.typeInfo, qualification.qualifierRequired(parameterizedType.typeInfo)));
            } else {
                OutputBuilder sub;
                if (parameterizedType.typeInfo.isPrimaryType() ||
                        inspectionProvider.getTypeInspection(parameterizedType.typeInfo).isStatic()) { // shortcut
                    sub = singleType(inspectionProvider, qualification, parameterizedType.typeInfo, diamond, false,
                            parameterizedType.parameters, visitedTypeParameters);
                } else {
                    sub = distributeTypeParameters(inspectionProvider, qualification, parameterizedType,
                            visitedTypeParameters, diamond);
                }
                outputBuilder.add(sub);
            }
        }
        if (!withoutArrays) {
            if (varargs) {
                if (parameterizedType.arrays == 0) {
                    throw new UnsupportedOperationException("Varargs parameterized types must have arrays>0!");
                }
                outputBuilder.add(new Text(("[]".repeat(parameterizedType.arrays - 1) + "...")));
            } else if (parameterizedType.arrays > 0) {
                outputBuilder.add(new Text("[]".repeat(parameterizedType.arrays)));
            }
        }
        return outputBuilder;
    }

    // if a type is a sub-type, the type parameters may belong to any of the intermediate types
    // we should write them there
    private static OutputBuilder distributeTypeParameters(InspectionProvider inspectionProvider,
                                                          Qualification qualification,
                                                          ParameterizedType parameterizedType,
                                                          Set<TypeParameter> visitedTypeParameters,
                                                          Diamond diamond) {
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
        return taps.stream().map(tap -> singleType(inspectionProvider, qualification,
                tap.typeInfo, diamond, !tap.isPrimaryType, tap.typeParameters, visitedTypeParameters))
                .collect(OutputBuilder.joining(Symbol.DOT));
    }

    static record TypeAndParameters(TypeInfo typeInfo, boolean isPrimaryType, List<ParameterizedType> typeParameters) {
    }

    private static OutputBuilder singleType(InspectionProvider inspectionProvider,
                                            Qualification qualification,
                                            TypeInfo typeInfo,
                                            Diamond diamond,
                                            boolean forceSimple, // when constructing an qualified with distributed type parameters
                                            List<ParameterizedType> typeParameters,
                                            Set<TypeParameter> visitedTypeParameters) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (forceSimple) {
            outputBuilder.add(new Text(typeInfo.simpleName));
        } else {
            outputBuilder.add(new TypeName(typeInfo, qualification.qualifierRequired(typeInfo)));
        }
        if (!typeParameters.isEmpty() && diamond != Diamond.NO) {
            outputBuilder.add(Symbol.LEFT_ANGLE_BRACKET);
            if (diamond == Diamond.SHOW_ALL) {
                outputBuilder.add(typeParameters.stream().map(tp -> print(inspectionProvider, qualification,
                        tp, false, Diamond.SHOW_ALL, false, visitedTypeParameters))
                        .collect(OutputBuilder.joining(Symbol.COMMA)));
            }
            outputBuilder.add(Symbol.RIGHT_ANGLE_BRACKET);
        }
        return outputBuilder;
    }
}
