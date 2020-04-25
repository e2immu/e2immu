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
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class FieldInfo implements WithInspectionAndAnalysis {

    public final ParameterizedType type;
    public final String name;

    public final TypeInfo owner;
    public final SetOnce<FieldInspection> fieldInspection = new SetOnce<>();
    public final FieldAnalysis fieldAnalysis = new FieldAnalysis();

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
    public Analysis getAnalysis() {
        return fieldAnalysis;
    }

    @Override
    public Inspection getInspection() {
        return fieldInspection.get();
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

    public Boolean isNotNull(TypeContext typeContext) {
        if (type.isPrimitive()) return true;
        if (owner.isNotNullForFields(typeContext) == Boolean.TRUE) return true;
        return annotatedWith(typeContext.notNull.get());
    }

    public Boolean isNotModified(TypeContext typeContext) {
        if (type.isPrimitive()) return true;
        Boolean isE2Immutable = isE2Immutable(typeContext);
        if (isE2Immutable == Boolean.TRUE) return true;
        if (isE2Immutable == null) return null; // delay
        return annotatedWith(typeContext.notModified.get());
    }

    public Boolean isEffectivelyFinal(TypeContext typeContext) {
        Boolean isE2Immutable = owner.isE2Immutable(typeContext);
        if (isE2Immutable == Boolean.TRUE) return true;
        // NOTE: we're not delaying here because of not knowing the E2Immutable status of the owner
        if (fieldInspection.isSet() && fieldInspection.get().modifiers.contains(FieldModifier.FINAL)) return true;
        return annotatedWith(typeContext.effectivelyFinal.get());
    }

    public Boolean isE2Immutable(TypeContext typeContext) {
        // we do not look at the type's annotations if we are of that type (self reference)
        Boolean staticType;
        if (type.typeInfo == owner) {
            staticType = false;
        } else {
            staticType = type.isE2Immutable(typeContext);
        }
        Boolean dynamicType = TypeAnalyser.TERNARY_OR.apply(annotatedWith(typeContext.e2Immutable.get()),
                annotatedWith((typeContext.e2Container.get())));
        return TypeAnalyser.TERNARY_OR.apply(staticType, dynamicType);
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

}
