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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.output.QualifiedName;
import org.e2immu.support.SetOnce;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Parameter info objects must be included in the context of a method info object... whence
 * the restriction on typeInfo and name as the equality fields.
 */
@Container
//@ContextClass(after="this.analyse()")
public class ParameterInfo implements Variable, WithInspectionAndAnalysis, Comparable<ParameterInfo> {

    public final ParameterizedType parameterizedType;
    public final String name;
    public final int index;

    public final SetOnce<ParameterAnalysis> parameterAnalysis = new SetOnce<>();
    public final SetOnce<ParameterInspection> parameterInspection = new SetOnce<>();
    public final MethodInfo owner;

    public ParameterInfo(MethodInfo owner, ParameterizedType parameterizedType, String name, int index) {
        // can be null, in lambda's
        this.parameterizedType = parameterizedType;
        this.name = Objects.requireNonNull(name);
        this.index = index;
        this.owner = Objects.requireNonNull(owner);
    }

    @Override
    public TypeInfo getOwningType() {
        return owner.typeInfo;
    }

    @Override
    public TypeInfo primaryType() {
        return owner.typeInfo.primaryType();
    }

    @Override
    public String toString() {
        return fullyQualifiedName();
    }

    @Override
    public ParameterizedType parameterizedType() {
        return parameterizedType;
    }

    @Override
    public ParameterizedType concreteReturnType() {
        return parameterizedType; // there's nothing more we can know; we're NOT treating it as a local variable!!!
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String simpleName() {
        return name;
    }

    @Override
    public String fullyQualifiedName() {
        return owner.fullyQualifiedName() + ":" + index + ":" + name;
    }

    @Override
    public String nameInLinkedAnnotation() {
        return owner.name + ":" + name;
    }

    public boolean hasBeenInspected() {
        return parameterInspection.isSet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterInfo that = (ParameterInfo) o;
        return index == that.index &&
                owner.equals(that.owner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, owner);
    }

    @Override
    public void setAnalysis(Analysis analysis) {
        parameterAnalysis.set((ParameterAnalysis) analysis);
    }

    @Override
    public Analysis getAnalysis() {
        return parameterAnalysis.get();
    }

    @Override
    public boolean hasBeenAnalysed() {
        return parameterAnalysis.isSet();
    }

    @Override
    public Inspection getInspection() {
        return parameterInspection.get();
    }

    // for now not using the shared method; directly adding the annotations
    public OutputBuilder outputDeclaration(Qualification qualification) {
        OutputBuilder outputBuilder = new OutputBuilder();
        ParameterInspection parameterInspection = this.parameterInspection.get();
        Set<TypeInfo> annotationsSeen = new HashSet<>();
        for (AnnotationExpression annotation : parameterInspection.getAnnotations()) {
            outputBuilder.add(annotation.output(qualification));
            if (parameterAnalysis.isSet()) {
                outputBuilder.add(parameterAnalysis.get().peekIntoAnnotations(annotation, annotationsSeen));
            }
            outputBuilder.add(Space.ONE);
        }
        if (parameterAnalysis.isSet()) {
            parameterAnalysis.get().getAnnotationStream().forEach(entry -> {
                boolean present = entry.getValue().isPresent();
                AnnotationExpression annotation = entry.getKey();
                if (present && !annotationsSeen.contains(annotation.typeInfo())) {
                    outputBuilder.add(annotation.output(qualification));
                    outputBuilder.add(Space.ONE);
                }
            });
        }
        if (parameterizedType != ParameterizedType.NO_TYPE_GIVEN_IN_LAMBDA) {
            outputBuilder.add(parameterizedType.output(qualification, parameterInspection.isVarArgs(), Diamond.SHOW_ALL));
            outputBuilder.add(Space.ONE);
        }
        outputBuilder.add(new Text(name));
        return outputBuilder;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public Optional<AnnotationExpression> hasInspectedAnnotation(Class<?> annotation) {
        String annotationFQN = annotation.getName();
        Optional<AnnotationExpression> fromParameter = getInspection().getAnnotations().stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(annotationFQN))
                .findFirst();
        if (fromParameter.isPresent()) return fromParameter;
        if (NotNull.class.equals(annotation)) {
            return owner.typeInfo.hasInspectedAnnotation(annotation);
        }
        return Optional.empty();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return SideEffect.NONE_PURE;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return typesReferenced(false);
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        return UpgradableBooleanMap.of(parameterizedType.typesReferenced(explicit),
                hasBeenInspected() ?
                        parameterInspection.get().getAnnotations().stream().flatMap(ae -> ae.typesReferenced().stream()).collect(UpgradableBooleanMap.collector())
                        : UpgradableBooleanMap.of());
    }

    // as variable
    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new QualifiedName(name, null, QualifiedName.Required.NEVER));
    }

    @Override
    public int compareTo(ParameterInfo o) {
        if (owner == o.owner) {
            return index - o.index;
        }
        throw new UnsupportedOperationException();
    }
}
