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

import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.SetOnce;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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
    public Analysis getAnalysis() {
        return fieldAnalysis.get();
    }

    @Override
    public boolean hasBeenAnalysed() {
        return fieldAnalysis.isSet();
    }

    @Override
    public Inspection getInspection() {
        return fieldInspection.get();
    }

    @Override
    public boolean hasBeenInspected() {
        return fieldInspection.isSet();
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                type.typesReferenced(true),
                fieldInspection.isSet() ? fieldInspection.get().getAnnotations().stream()
                        .flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector())
                        : UpgradableBooleanMap.of(),
                hasBeenAnalysed() ? fieldAnalysis.get().getAnnotationStream()
                        .filter(e -> e.getValue().isVisible())
                        .flatMap(e -> e.getKey().typesReferenced().stream())
                        .collect(UpgradableBooleanMap.collector()) : UpgradableBooleanMap.of(),
                fieldInspection.isSet() && fieldInspection.get().fieldInitialiserIsSet() ?
                        fieldInspection.get().getFieldInitialiser().initialiser().typesReferenced()
                        : UpgradableBooleanMap.of()
        );
    }

    @Override
    public TypeInfo primaryType() {
        return owner.primaryType();
    }

    public OutputBuilder output(Qualification qualification, boolean asParameter) {
        Stream<OutputBuilder> annotationStream = buildAnnotationOutput(qualification);

        OutputBuilder outputBuilder = new OutputBuilder();
        if (fieldInspection.isSet() && !asParameter) {
            FieldInspection inspection = this.fieldInspection.get();
            outputBuilder.add(Arrays.stream(FieldModifier.sort(inspection.getModifiers()))
                    .map(mod -> new OutputBuilder().add(new Text(mod)))
                    .collect(OutputBuilder.joining(Space.ONE)));
            if (!inspection.getModifiers().isEmpty()) outputBuilder.add(Space.ONE);
        }
        outputBuilder
                .add(type.output(qualification))
                .add(Space.ONE)
                .add(new Text(name));
        if (!asParameter && fieldInspection.isSet() && fieldInspection.get().fieldInitialiserIsSet()) {
            Expression expression = fieldInspection.get().getFieldInitialiser().initialiser();
            if (expression != EmptyExpression.EMPTY_EXPRESSION) {
                outputBuilder.add(Symbol.assignment("=")).add(expression.output(qualification));
            }
        }
        if (!asParameter) {
            outputBuilder.add(Symbol.SEMICOLON);
        }

        return Stream.concat(annotationStream, Stream.of(outputBuilder)).collect(OutputBuilder.joining(Space.ONE_REQUIRED_EASY_SPLIT,
                Guide.generatorForAnnotationList()));
    }

    public boolean isStatic() {
        return fieldInspection.get().getModifiers().contains(FieldModifier.STATIC);
    }

    public boolean isStatic(InspectionProvider inspectionProvider) {
        FieldInspection inspection = inspectionProvider.getFieldInspection(this);
        assert inspection != null : "No field inspection known for " + fullyQualifiedName();
        return inspection.getModifiers().contains(FieldModifier.STATIC);
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

    public boolean isAccessibleOutsideOfPrimaryType() {
        return !fieldInspection.get().getModifiers().contains(FieldModifier.PRIVATE) &&
                !owner.isPrivateOrEnclosingIsPrivate();
    }

    public boolean isPublic() {
        return fieldInspection.get().getModifiers().contains(FieldModifier.PUBLIC);
    }

    public boolean isPrivate() {
        return fieldInspection.get().getModifiers().contains(FieldModifier.PRIVATE);
    }
}
