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

import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.*;

public class FieldInfo implements WithInspectionAndAnalysis {

    public final ParameterizedType type;
    public final String name;

    public final TypeInfo owner;
    public final SetOnce<FieldInspection> fieldInspection = new SetOnce<>();
    public final SetOnce<FieldAnalysis> fieldAnalysis = new SetOnce<>();

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
    public void setAnalysis(Analysis analysis) {
        fieldAnalysis.set((FieldAnalysis) analysis);
    }

    @Override
    public Inspection getInspection() {
        return fieldInspection.get();
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                type.typesReferenced(true),
                fieldInspection.isSet() && fieldInspection.get().fieldInitialiserIsSet() ?
                        fieldInspection.get().getFieldInitialiser().initialiser().typesReferenced()
                        : UpgradableBooleanMap.of()
        );
    }

    @Override
    public TypeInfo primaryType() {
        return owner.primaryType();
    }

    public OutputBuilder output() {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (fieldInspection.isSet()) {
            FieldInspection inspection = this.fieldInspection.get();
            outputAnnotations(inspection, outputBuilder);
            outputBuilder.add(Arrays.stream(FieldModifier.sort(inspection.getModifiers())).map(Text::new)
                    .collect(OutputBuilder.joinElements(Space.ONE)));
            if (!inspection.getModifiers().isEmpty()) outputBuilder.add(Space.ONE);
        }
        outputBuilder
                .add(type.output())
                .add(Space.ONE)
                .add(new Text(name));
        if (fieldInspection.isSet() && fieldInspection.get().fieldInitialiserIsSet()) {
            Expression expression = fieldInspection.get().getFieldInitialiser().initialiser();
            if (expression != EmptyExpression.EMPTY_EXPRESSION) {
                outputBuilder.add(Symbol.assignment("=")).add(expression.output());
            }
        }
        return outputBuilder.add(Symbol.SEMICOLON);
    }

    private void outputAnnotations(FieldInspection inspection, OutputBuilder outputBuilder) {
        Guide.GuideGenerator guideGenerator = new Guide.GuideGenerator();
        outputBuilder.add(guideGenerator.start());
        Set<TypeInfo> annotationsSeen = new HashSet<>();
        inspection.getAnnotations().forEach(ae -> {
            outputBuilder.add(guideGenerator.mid()).add(ae.output());
            if (fieldAnalysis.isSet()) {
                outputBuilder.add(fieldAnalysis.get().peekIntoAnnotations(ae, annotationsSeen));
            }
            outputBuilder.add(Space.ONE_REQUIRED_EASY_SPLIT);
        });
        if (fieldAnalysis.isSet()) {
            fieldAnalysis.get().getAnnotationStream().forEach(entry -> {
                boolean present = entry.getValue();
                AnnotationExpression annotation = entry.getKey();
                if (present && !annotationsSeen.contains(annotation.typeInfo())) {
                    outputBuilder.add(guideGenerator.mid()).add(annotation.output()).add(Space.ONE_REQUIRED_EASY_SPLIT);
                }
            });
        }
        outputBuilder.add(guideGenerator.end());
    }

    public boolean isStatic() {
        return fieldInspection.isSet() && fieldInspection.get().getModifiers().contains(FieldModifier.STATIC);
    }

    @Override
    public Optional<AnnotationExpression> hasInspectedAnnotation(Class<?> annotation) {
        if (!fieldInspection.isSet()) return Optional.empty();
        String annotationFQN = annotation.getName();
        Optional<AnnotationExpression> fromField = (getInspection().getAnnotations().stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(annotationFQN))).findFirst();
        if (fromField.isPresent()) return fromField;
        if (annotation.equals(NotNull.class)) return owner.hasInspectedAnnotation(annotation);
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
        return fieldInspection.get().getModifiers().contains(FieldModifier.FINAL);
    }

    public boolean isPrivate() {
        return fieldInspection.get().getModifiers().contains(FieldModifier.PRIVATE);
    }

    public Set<ParameterizedType> explicitTypes() {
        if (!fieldInspection.get().fieldInitialiserIsSet()) return Set.of();
        FieldInspection.FieldInitialiser fieldInitialiser = fieldInspection.get().getFieldInitialiser();
        // SAMs are handled by the method code
        return MethodInfo.explicitTypes(fieldInitialiser.initialiser());
    }
}
