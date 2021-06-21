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

package org.e2immu.analyser.usage;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Given a collection of types, find the references to types, methods and fields from a number of packages.
 * This method is used to make customised AnnotatedAPI files.
 * <p>
 * At some point we'll need to make a generic search/iterate system that can be reused more.
 */
public record CollectUsages(List<String> packagePrefixes, Set<String> packagesAccepted) {

    public CollectUsages(List<String> packagePrefixes) {
        this(packagePrefixes.stream().filter(pp -> pp.endsWith(".")).toList(),
                packagePrefixes.stream().map(pp -> pp.endsWith(".") ? pp.substring(0, pp.length() - 1) : pp)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    public Set<WithInspectionAndAnalysis> collect(Collection<TypeInfo> types) {
        Set<WithInspectionAndAnalysis> result = new HashSet<>();
        for (TypeInfo type : types) {
            collect(result, type);
        }
        return result;
    }

    private void collect(Set<WithInspectionAndAnalysis> result, TypeInfo type) {
        TypeInspection inspection = type.typeInspection.get();
        inspection.subTypes().forEach(subType -> collect(result, subType));
        inspection.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                .forEach(methodInfo -> collect(result, methodInfo));
        inspection.fields().forEach(fieldInfo -> collect(result, fieldInfo));
        inspection.typeParameters().forEach(typeParameter -> collect(result, typeParameter));
    }

    private void collect(Set<WithInspectionAndAnalysis> result, MethodInfo methodInfo) {
        MethodInspection inspection = methodInfo.methodInspection.get();
        inspection.getParameters().forEach(parameterInfo -> collect(result, parameterInfo.parameterizedType));
        if (methodInfo.hasReturnValue()) {
            collect(result, inspection.getReturnType());
        }
        inspection.getTypeParameters().forEach(typeParameter -> collect(result, typeParameter));
        collect(result, inspection.getMethodBody());
    }

    private void collect(Set<WithInspectionAndAnalysis> result, FieldInfo fieldInfo) {
        collect(result, fieldInfo.type);
        FieldInspection inspection = fieldInfo.fieldInspection.get();
        if (inspection.fieldInitialiserIsSet()) {
            FieldInspection.FieldInitialiser fieldInitialiser = inspection.getFieldInitialiser();
            if (fieldInitialiser.initialiser() != null) {
                collect(result, fieldInitialiser.initialiser());
            }
        }
        if (accept(fieldInfo.owner.packageName())) {
            result.add(fieldInfo);
        }
    }

    private void collect(Set<WithInspectionAndAnalysis> result, TypeParameter typeParameter) {
        typeParameter.getTypeBounds().forEach(typeBound -> {
            if (typeBound.typeInfo != null && accept(typeBound.typeInfo.packageName())) {
                result.add(typeBound.typeInfo);
            }
            typeBound.parameters.forEach(pt -> collect(result, pt));
        });
    }

    private void collect(Set<WithInspectionAndAnalysis> result, ParameterizedType type) {
        TypeInfo bestType = type.bestTypeInfo();
        if (bestType != null && accept(bestType.packageName())) {
            result.add(bestType);
        }
        type.parameters.forEach(parameter -> collect(result, parameter));
    }

    private void collect(Set<WithInspectionAndAnalysis> result, Element element) {
        element.visit(e -> {
            VariableExpression ve;
            NewObject no;
            MethodReference mr;
            MethodCall mc;
            if ((mc = e.asInstanceOf(MethodCall.class)) != null && accept(mc.methodInfo.typeInfo.packageName())) {
                result.add(mc.methodInfo);
                result.add(mc.methodInfo.typeInfo);
            } else if ((mr = e.asInstanceOf(MethodReference.class)) != null && accept(mr.methodInfo.typeInfo.packageName())) {
                result.add(mr.methodInfo);
                result.add(mr.methodInfo.typeInfo);
            } else if ((no = e.asInstanceOf(NewObject.class)) != null && no.constructor() != null &&
                    accept(no.constructor().typeInfo.packageName())) {
                result.add(no.constructor());
                result.add(no.constructor().typeInfo);
            } else if ((ve = e.asInstanceOf(VariableExpression.class)) != null) {
                if (ve.variable() instanceof FieldReference fr) {
                    collect(result, fr.fieldInfo);
                    if (fr.scope != null) {
                        collect(result, fr.scope);
                    }
                }
                collect(result, ve.variable().parameterizedType());
            } else if (e instanceof TypeExpression te) {
                collect(result, te.parameterizedType);
            }
        });
    }

    private boolean accept(String packageName) {
        return packagesAccepted.contains(packageName) || packagePrefixes.stream().anyMatch(packageName::startsWith);
    }
}