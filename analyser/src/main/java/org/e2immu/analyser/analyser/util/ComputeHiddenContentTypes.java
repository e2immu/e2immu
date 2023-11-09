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
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.SetOfTypes;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ComputeHiddenContentTypes {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeHiddenContentTypes.class);

    private final AnalyserContext analyserContext;
    private final TypeInfo typeInfo;
    private final Set<ParameterizedType> types = new HashSet<>();
    private final List<CausesOfDelay> delays = new LinkedList<>();

    public ComputeHiddenContentTypes(TypeInfo typeInfo, AnalyserContext analyserContext) {
        this.analyserContext = analyserContext;
        this.typeInfo = typeInfo;
    }

    public ComputeHiddenContentTypes go(SetOfTypes typeParameters) {
        types.addAll(typeParameters.types()); // these are the unbound type parameters of the type
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

        - when shallow and not abstract, go over the types of the publicly accessible methods = STOPGAP measure
        - otherwise, go over the types of the fields.
         */
        if (typeInfo.shallowAnalysis() && !typeInspection.isInterface()) {
            for (MethodInfo methodInfo : typeInspection.methods()) {
                MethodInspection methodInspection = analyserContext.getMethodInspection(methodInfo);
                if (methodInspection.isPubliclyAccessible()) {
                    if (!methodInfo.isConstructor() && !methodInspection.isVoid())
                        addType(methodInspection.getReturnType(), currentType);
                    for (ParameterInfo pi : methodInspection.getParameters()) {
                        addType(pi.parameterizedType, currentType);
                    }
                }
            }
        } else {
            for (FieldInfo fieldInfo : typeInspection.fields()) {
                addType(fieldInfo.type, currentType);
            }
        }
    }

    private void addType(ParameterizedType typeWithArray, TypeInfo currentType) {
        ParameterizedType type = typeWithArray.copyWithoutArrays();
        TypeInfo bestType = type.bestTypeInfo();
        if (bestType == null || bestType == currentType)
            return; // do not add type parameters! the relevant ones have been added already

        DV immutable = analyserContext.typeImmutable(type);
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
            // shortcut: as soon as a type is recursively immutable, we can't add anymore
            return;
        }
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis != null) {
            if (typeAnalysis.hiddenContentDelays().isDelayed()) {
                LOGGER.debug("Delaying hidden content computation, waiting for HC of {}", bestType);
                delays.add(typeAnalysis.hiddenContentDelays().causesOfDelay());
            } else {
                SetOfTypes hidden = typeAnalysis.getHiddenContentTypes().translate(analyserContext, type);
                if (immutable.isDelayed()) {
                    LOGGER.debug("Delaying hidden content computation, waiting for IMMUTABLE of {}", bestType);
                    delays.add(immutable.causesOfDelay());
                } else {
                    if (MultiLevel.isAtLeastEventuallyImmutableHC(immutable)) {
                       /*
                        Look at the hidden content of this type. If it is empty, add this type.
                        Otherwise, recurse, and don't add this type, it merely encapsulates.
                        */
                        if (hidden.isEmpty()) {
                            types.add(type);
                            return;
                        }
                    } // else: mutable, final fields: recurse only
                    hidden.types().stream().filter(t -> !type.equals(t) && t.typeInfo != currentType)
                            .forEach(t -> addType(t, currentType));
                }
            } // else: ignore
        }
    }

    public CausesOfDelay delays() {
        return delays.stream().reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }

    public SetOfTypes build() {
        return new SetOfTypes(types);
    }
}
