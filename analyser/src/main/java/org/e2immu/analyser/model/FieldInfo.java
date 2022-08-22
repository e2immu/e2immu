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

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.support.SetOnce;

import java.util.*;
import java.util.stream.Stream;

public class FieldInfo implements WithInspectionAndAnalysis {
    private final Identifier identifier;
    public final ParameterizedType type;
    public final String name;

    public final TypeInfo owner;
    public final SetOnce<FieldInspection> fieldInspection = new SetOnce<>();
    public final SetOnce<FieldAnalysis> fieldAnalysis = new SetOnce<>();
    public final String fullyQualifiedName;

    public FieldInfo(Identifier identifier, ParameterizedType type, String name, TypeInfo owner) {
        this.type = Objects.requireNonNull(type);
        this.name = Objects.requireNonNull(name);
        this.owner = Objects.requireNonNull(owner);
        this.identifier = Objects.requireNonNull(identifier);
        this.fullyQualifiedName = owner.fullyQualifiedName + "." + name;
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
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
    public TypeInfo getTypeInfo() {
        return owner;
    }

    @Override
    public int hashCode() {
        return fullyQualifiedName.hashCode();
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
            List<FieldModifier> fieldModifiers = minimalModifiers(inspection);
            outputBuilder.add(fieldModifiers.stream()
                    .map(mod -> new OutputBuilder().add(new Text(mod.toJava())))
                    .collect(OutputBuilder.joining(Space.ONE)));
            if (!fieldModifiers.isEmpty()) outputBuilder.add(Space.ONE);
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

    private List<FieldModifier> minimalModifiers(FieldInspection fieldInspection) {
        Set<FieldModifier> modifiers = fieldInspection.getModifiers();
        List<FieldModifier> list = new ArrayList<>();
        Inspection.Access access = fieldInspection.getAccess();
        Inspection.Access ownerAccess = owner.typeInspection.get().getAccess();

        /*
        if the owner access is private, we don't write any modifier
         */
        if (access.le(ownerAccess) && access != Inspection.Access.PACKAGE && ownerAccess != Inspection.Access.PRIVATE) {
            list.add(toFieldModifier(access));
        }
        for (FieldModifier fm : FieldModifier.NON_ACCESS_SORTED) {
            if (modifiers.contains(fm)) list.add(fm);
        }
        return list;
    }

    private static FieldModifier toFieldModifier(Inspection.Access access) {
        return switch (access) {
            case PUBLIC -> FieldModifier.PUBLIC;
            case PRIVATE -> FieldModifier.PRIVATE;
            case PROTECTED -> FieldModifier.PROTECTED;
            default -> throw new UnsupportedOperationException();
        };
    }

    public String fullyQualifiedName() {
        return fullyQualifiedName;
    }

    @Override
    public String name() {
        return name;
    }

    public boolean isExplicitlyFinal() {
        return fieldInspection.get().getModifiers().contains(FieldModifier.FINAL);
    }

    @Override
    public String niceClassName() {
        return "Field";
    }

    @Override
    public Location newLocation() {
        return new LocationImpl(this);
    }

    @Override
    public CausesOfDelay delay(CauseOfDelay.Cause cause) {
        return DelayFactory.createDelay(newLocation(), cause);
    }
}
