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

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetTwice;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class FieldInfo implements WithInspectionAndAnalysis {

    public final ParameterizedType type;
    public final String name;

    public final TypeInfo owner;
    public final SetTwice<FieldInspection> fieldInspection = new SetTwice<>();
    public final SetOnce<FieldAnalysis> fieldAnalysis = new SetOnce<>();

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
    public String toString() {
        return name;
    }

    @Override
    public void setAnalysis(IAnalysis analysis) {
        fieldAnalysis.set((FieldAnalysis) analysis);
    }

    @Override
    public Inspection getInspection() {
        return fieldInspection.get();
    }

    @Override
    public boolean hasBeenDefined() {
        return owner.hasBeenDefined() && (!owner.isInterface() || fieldInspection.get().haveInitialiser());
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                type.typesReferenced(true),
                fieldInspection.isSet() && fieldInspection.get().initialiser.isSet() ?
                        fieldInspection.get().initialiser.get().initialiser.typesReferenced()
                        : UpgradableBooleanMap.of()
        );
    }

    public String stream(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        if (fieldInspection.isSet()) {
            Set<TypeInfo> annotationsSeen = new HashSet<>();
            fieldInspection.get().annotations.forEach(ae -> {
                sb.append(ae.stream());
                if (fieldAnalysis.isSet()) {
                    fieldAnalysis.get().peekIntoAnnotations(ae, annotationsSeen, sb);
                }
                sb.append("\n");
                StringUtil.indent(sb, indent);
            });
            if (fieldAnalysis.isSet()) {
                fieldAnalysis.get().getAnnotationStream().forEach(entry -> {
                    boolean present = entry.getValue();
                    AnnotationExpression annotation = entry.getKey();
                    if (present && !annotationsSeen.contains(annotation.typeInfo)) {
                        sb.append(annotation.stream());
                        sb.append("\n");
                        StringUtil.indent(sb, indent);
                    }
                });
            }
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
        if (annotation.equals(NotNull.class)) return owner.hasTestAnnotation(annotation);
        // TODO check "where" on @NotNull
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

    public Messages copyAnnotationsIntoFieldAnalysisProperties(E2ImmuAnnotationExpressions typeContext) {

        FieldAnalysisImpl.Builder fieldAnalysisBuilder = new FieldAnalysisImpl.Builder(AnalysisProvider.DEFAULT_PROVIDER,
                this, owner.typeAnalysis.get());
        boolean acceptVerify = !owner.hasBeenDefined() || owner.isInterface();
        Messages messages = new Messages();
        messages.addAll(fieldAnalysisBuilder.fromAnnotationsIntoProperties(acceptVerify, fieldInspection.get().annotations,
                typeContext));
        /*if (fieldInspection.get().initialiser.isSet() &&
                fieldInspection.get().initialiser.get().implementationOfSingleAbstractMethod != null) {
            messages.addAll(fieldInspection.get().initialiser.get()
                    .implementationOfSingleAbstractMethod.typeInfo
                    .copyAnnotationsIntoTypeAnalysisProperties(typeContext, overwrite, "field"));
        } has already been set at creation time */

        // the following code is here to save some @Final annotations in annotated APIs where there already is a `final` keyword.
        int effectivelyFinal = fieldAnalysisBuilder.getProperty(VariableProperty.FINAL);
        if (isExplicitlyFinal() && effectivelyFinal != Level.TRUE) {
            fieldAnalysisBuilder.improveProperty(VariableProperty.FINAL, Level.TRUE);
        }
        setAnalysis(fieldAnalysisBuilder.build());

        return messages;
    }

    public Set<ParameterizedType> explicitTypes() {
        if (!fieldInspection.get().initialiser.isSet()) return Set.of();
        FieldInspection.FieldInitialiser fieldInitialiser = fieldInspection.get().initialiser.get();
        // SAMs are handled by the method code
        return MethodInfo.explicitTypes(fieldInitialiser.initialiser);
    }
}
