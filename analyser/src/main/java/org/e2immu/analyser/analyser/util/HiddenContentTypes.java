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

package org.e2immu.analyser.analyser.util;


import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.SetOfTypes;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HiddenContentTypes {
    private final AnalyserContext analyserContext;
    private final TypeInfo typeInfo;
    private final Set<ParameterizedType> types = new HashSet<>();
    private final Set<TypeInfo> hierarchyToAvoid = new HashSet<>();

    public HiddenContentTypes(TypeInfo typeInfo, AnalyserContext analyserContext) {
        this.analyserContext = analyserContext;
        this.typeInfo = typeInfo;
    }

    public HiddenContentTypes go(SetOfTypes transparentTypes) {
        types.addAll(transparentTypes.types()); // these are the unbound type parameters of the type
        if (!typeInfo.isJavaLangObject()) {
            recurse(typeInfo);
        }
        return this;
    }

    /*
    We recurse first, to build up the "hierarchyToAvoid" set.
    When we recurse, we need to ensure that new type parameters are ignored!
     */
    private void recurse(TypeInfo currentType) {
        hierarchyToAvoid.add(currentType);
        TypeInspection typeInspection = analyserContext.getTypeInspection(currentType);
        ParameterizedType parent = typeInspection.parentClass();
        if (!parent.isJavaLangObject()) {
            recurse(parent.typeInfo);
        }
        List<ParameterizedType> interfaces = typeInspection.interfacesImplemented();
        for (ParameterizedType interfaceImplemented : interfaces) {
            recurse(interfaceImplemented.typeInfo);
        }
        /* and now choose the algorithm:

        - when shallow or interface, go over the types of the publicly accessible methods.
        - otherwise, go over the types of the fields.
         */
        if (currentType.isInterface() || typeInfo.shallowAnalysis()) {
            for (MethodInfo methodInfo : typeInspection.methods()) {
                MethodInspection methodInspection = analyserContext.getMethodInspection(methodInfo);
                if (methodInspection.isPubliclyAccessible()) {
                    if (!methodInfo.isConstructor && !methodInspection.isVoid())
                        addType(methodInspection.getReturnType());
                    for (ParameterInfo pi : methodInspection.getParameters()) {
                        addType(pi.parameterizedType);
                    }
                }
            }
        } else {
            for (FieldInfo fieldInfo : typeInspection.fields()) {
                addType(fieldInfo.type);
            }
        }
    }

    private void addType(ParameterizedType type) {
        if (type.isPrimitiveExcludingVoid() || type.isJavaLangObject()) {
            return;
        }
        TypeInfo bestType = type.bestTypeInfo();
        if (bestType == null) return; // do not add type parameters! the relevant ones have been added already
        if (hierarchyToAvoid.contains(bestType)) {
            return;
        }
        DV immutable = analyserContext.defaultImmutable(type, false, typeInfo);
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) return;
        if (MultiLevel.isAtLeastEventuallyE2Immutable(immutable)) {
            types.add(type);
        } else {
            // so we end up with a mutable/E1 type...
            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
            if (typeAnalysis == null || typeAnalysis.transparentAndExplicitTypeComputationDelays().isDelayed()) {
                //?
            } else {
                /*
                the naive approach (simply adding all the hidden content types) doesn't work, 'type' will have
                concrete values for any type parameter!
                 */
                SetOfTypes hidden = typeAnalysis.getHiddenContentTypes();
                types.addAll(hidden.types().stream().filter(ParameterizedType::isType).toList());
            }
        }
    }

    public SetOfTypes build() {
        return new SetOfTypes(types);
    }
}
