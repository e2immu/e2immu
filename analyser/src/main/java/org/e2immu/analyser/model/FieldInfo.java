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

import org.e2immu.analyser.analyser.TypeAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.IgnoreModifications;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class FieldInfo implements WithInspectionAndAnalysis {

    public final ParameterizedType type;
    public final String name;

    public final TypeInfo owner;
    public final SetOnce<FieldInspection> fieldInspection = new SetOnce<>();
    public final FieldAnalysis fieldAnalysis;

    public FieldInfo(TypeInfo type, String name, TypeInfo owner) {
        this(type.asParameterizedType(), name, owner);
    }

    public FieldInfo(ParameterizedType type, String name, TypeInfo owner) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(name);
        Objects.requireNonNull(owner);

        this.type = type;
        this.name = name;
        this.owner = owner;
        fieldAnalysis = new FieldAnalysis(type.bestTypeInfo(), owner);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldInfo fieldInfo = (FieldInfo) o;
        return name.equals(fieldInfo.name) &&
                owner.equals(fieldInfo.owner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, owner);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public Analysis getAnalysis() {
        return fieldAnalysis;
    }

    @Override
    public Inspection getInspection() {
        return fieldInspection.get();
    }

    @Override
    public boolean hasBeenDefined() {
        return owner.hasBeenDefined() && (!owner.isInterface() || fieldInspection.get().haveInitialiser());
    }

    public Set<String> imports() {
        if (type.isTypeParameter()) return Collections.emptySet();
        TypeInfo typeInfo = type.typeInfo;
        if (typeInfo.isPrimitive() || typeInfo.isJavaLang()) {
            return Collections.emptySet();
        }
        return Collections.singleton(typeInfo.fullyQualifiedName);
    }

    public String stream(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        if (fieldInspection.isSet()) {
            Set<TypeInfo> annotationsSeen = new HashSet<>();
            fieldInspection.get().annotations.forEach(ae -> {
                sb.append(ae.stream());
                fieldAnalysis.peekIntoAnnotations(ae, annotationsSeen, sb);
                sb.append("\n");
                StringUtil.indent(sb, indent);
            });
            fieldAnalysis.annotations.visit((annotation, present) -> {
                if (present && !annotationsSeen.contains(annotation.typeInfo)) {
                    sb.append(annotation.stream());
                    sb.append("\n");
                    StringUtil.indent(sb, indent);
                }
            });
            FieldInspection fieldInspection = this.fieldInspection.get();
            sb.append(fieldInspection.modifiers.stream().map(m -> m.toJava() + " ").collect(Collectors.joining()));
        }
        sb.append(type.stream())
                .append(" ")
                .append(name);
        if (fieldInspection.isSet() && fieldInspection.get().initialiser.isSet()) {
            Expression expression = fieldInspection.get().initialiser.get().initialiser;
            if (expression != EmptyExpression.EMPTY_EXPRESSION) {
                sb.append(" = ");
                sb.append(expression.expressionString(indent));
            }
        }
        sb.append(";");
        return sb.toString();
    }

    public boolean isStatic() {
        return fieldInspection.isSet() && fieldInspection.get().modifiers.contains(FieldModifier.STATIC);
    }

    @Override
    public Optional<AnnotationExpression> hasTestAnnotation(Class<?> annotation) {
        if (!hasBeenDefined()) return Optional.empty();
        String annotationFQN = annotation.getName();
        Optional<AnnotationExpression> fromField = (getInspection().annotations.stream()
                .filter(ae -> ae.typeInfo.fullyQualifiedName.equals(annotationFQN))).findFirst();
        if (fromField.isPresent()) return fromField;
        if (annotation.equals(NotNull.class) || annotation.equals(NotModified.class))
            return owner.hasTestAnnotation(annotation);
        // TODO check "where" on @NotModified
        return Optional.empty();
    }

    public String fullyQualifiedName() {
        return owner.fullyQualifiedName + "." + name;
    }

    @Override
    public String name() {
        return name;
    }

    public boolean isExplicitlyFinal() {
        return fieldInspection.get().modifiers.contains(FieldModifier.FINAL);
    }

    public boolean isPrivate() {
        return fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE);
    }

    public int minimalValueByDefinition(VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_MODIFIED:
                if (type.isNotModifiedByDefinition()) return Level.TRUE;
                TypeInfo bestInfo = type.bestTypeInfo();
                if (bestInfo != null && Level.value(bestInfo.typeAnalysis.getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE) == Level.TRUE) {
                    // in an @E2Immutable class, all fields are @NotModified, so no need to write this
                    return Level.TRUE;
                }
                return Level.UNDEFINED;
            case NOT_NULL:
                if (type.isPrimitive()) return Level.TRUE;
                return Level.UNDEFINED;
            case FINAL:
                if (isExplicitlyFinal()) return Level.TRUE;
                if (Level.value(owner.typeAnalysis.getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE) == Level.TRUE) {
                    // in an @E2Immutable class, all fields are effectively final, so no need to write this
                    return Level.TRUE;
                }
            case IMMUTABLE:
            case CONTAINER:
                if (type.isE2ContainerByDefinition()) return variableProperty.best;

            default:
        }
        return Level.UNDEFINED;
    }
}
