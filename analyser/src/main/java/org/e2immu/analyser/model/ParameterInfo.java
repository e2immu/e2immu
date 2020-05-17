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

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.Container;

import java.util.*;

/**
 * Parameter info objects must be included in the context of a method info object... whence
 * the restriction on typeInfo and name as the equality fields.
 */
@Container
//@ContextClass(after="this.analyse()")
public class ParameterInfo implements Variable, WithInspectionAndAnalysis {

    public final ParameterizedType parameterizedType;
    public final String name;
    public final int index;

    public final SetOnce<ParameterAnalysis> parameterAnalysis = new SetOnce<>();
    public final SetOnce<ParameterInspection> parameterInspection = new SetOnce<>();

    public ParameterInfo(MethodInfo owner, TypeInfo typeInfo, String name, int index) {
        this(owner, typeInfo.asParameterizedType(), name, index);
    }

    public ParameterInfo(MethodInfo owner, ParameterizedType parameterizedType, String name, int index) {
        // can be null, in lambda's
        this.parameterizedType = parameterizedType;
        this.name = Objects.requireNonNull(name);
        this.index = index;
    }

    @Override
    public String toString() {
        return index + ":" + name;
    }

    @Override
    public ParameterizedType parameterizedType() {
        return parameterizedType;
    }

    @Override
    public ParameterizedType concreteReturnType() {
        return parameterizedType; // there's nothing more we can know; we're NOT treating it as a local variable!!!
        // TODO or should we surf on the concrete information in the method?
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String detailedString() {
        return name + " (parameter " + index + ")";
    }

    public boolean hasBeenInspected() {
        return parameterInspection.isSet();
    }

    @Override
    public boolean hasBeenDefined() {
        return parameterInspection.get().owner.hasBeenDefined();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterInfo that = (ParameterInfo) o;
        return parameterizedType.equals(that.parameterizedType) &&
                name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, name);
    }

    @Override
    public Analysis getAnalysis() {
        return parameterAnalysis.get();
    }

    @Override
    public Inspection getInspection() {
        return parameterInspection.get();
    }

    // the in-line method
    public String stream() {
        StringBuilder sb = new StringBuilder();
        ParameterInspection parameterInspection = this.parameterInspection.get();
        Set<TypeInfo> annotationsSeen = new HashSet<>();
        for (AnnotationExpression annotation : parameterInspection.annotations) {
            sb.append(annotation.stream());
            if (parameterAnalysis.isSet()) {
                parameterAnalysis.get().peekIntoAnnotations(annotation, annotationsSeen, sb);
            }
            sb.append(" ");
        }
        if (parameterAnalysis.isSet()) {
            parameterAnalysis.get().annotations.visit((annotation, present) -> {
                if (present && !annotationsSeen.contains(annotation.typeInfo)) {
                    sb.append(annotation.stream()).append(" ");
                }
            });
        }
        if (parameterizedType != ParameterizedType.NO_TYPE_GIVEN_IN_LAMBDA) {
            sb.append(parameterizedType.stream(parameterInspection.varArgs));
            sb.append(" ");
        }
        sb.append(name);
        return sb.toString();
    }

    public Set<String> imports() {
        Set<String> imports = new HashSet<>();
        if (hasBeenInspected()) {
            for (AnnotationExpression annotationExpression : parameterInspection.get().annotations) {
                imports.addAll(annotationExpression.imports());
            }
        }
        imports.addAll(parameterizedType.imports());
        return imports;
    }

    public void inspect(MethodInfo owner, Parameter parameter, ExpressionContext expressionContext, boolean isVarArgs) {
        ParameterInspection.ParameterInspectionBuilder builder = new ParameterInspection.ParameterInspectionBuilder();
        for (AnnotationExpr ae : parameter.getAnnotations()) {
            builder.addAnnotation(AnnotationExpression.from(ae, expressionContext));
        }
        builder.setVarArgs(isVarArgs);
        parameterInspection.set(builder.build(owner));
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public Optional<AnnotationExpression> hasTestAnnotation(Class<?> annotation) {
        if (!hasBeenDefined()) return Optional.empty();
        String annotationFQN = annotation.getName();
        Optional<AnnotationExpression> fromParameter = (getInspection().annotations.stream()
                .filter(ae -> ae.typeInfo.fullyQualifiedName.equals(annotationFQN))).findFirst();
        if (fromParameter.isPresent()) return fromParameter;
        return parameterInspection.get().owner.typeInfo.hasTestAnnotation(annotation);
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return SideEffect.NONE_PURE;
    }

    public int minimalValueByDefinition(VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.NOT_NULL) {
            MethodInfo owner = parameterInspection.get().owner;
            if (Level.haveTrueAt(owner.typeInfo.typeAnalysis.get().getProperty(VariableProperty.NOT_NULL_PARAMETERS), Level.NOT_NULL))
                return Level.TRUE;
        }
        if (variableProperty == VariableProperty.NOT_MODIFIED) {
            return parameterizedType.isNotModifiedByDefinition() ? Level.TRUE : Level.UNDEFINED;
        }
        if (variableProperty == VariableProperty.CONTAINER || variableProperty == VariableProperty.IMMUTABLE) {
            return parameterizedType.isE2ContainerByDefinition() ? variableProperty.best : Level.UNDEFINED;
        }
        return Level.UNDEFINED;
    }
}
