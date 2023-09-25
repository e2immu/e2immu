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

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.analyser.util.UpgradableIntMap;
import org.e2immu.annotation.Container;
import org.e2immu.support.SetOnce;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Parameter info objects must be included in the context of a method info object... whence
 * the restriction on typeInfo and name as the equality fields.
 */
@Container
//@ContextClass(after="this.analyse()")
public class ParameterInfo implements Variable, WithInspectionAndAnalysis {
    public final Identifier identifier;
    public final ParameterizedType parameterizedType;
    public final String name;
    public final String fullyQualifiedName;
    public final int index;

    public final SetOnce<ParameterAnalysis> parameterAnalysis = new SetOnce<>();
    public final SetOnce<ParameterInspection> parameterInspection = new SetOnce<>();
    public final MethodInfo owner;

    public ParameterInfo(Identifier identifier,
                         MethodInfo owner,
                         ParameterizedType parameterizedType,
                         String name,
                         int index) {
        this.identifier = Objects.requireNonNull(identifier);
        // can be null, in lambda's
        this.parameterizedType = parameterizedType;
        this.name = Objects.requireNonNull(name);
        assert !"this".equals(name); // to protect against bugs in bytecode reader
        this.index = index;
        this.owner = Objects.requireNonNull(owner);
        this.fullyQualifiedName = owner.fullyQualifiedName() + ":" + index + ":" + name;
    }

    @Override
    public TypeInfo getTypeInfo() {
        return owner.typeInfo;
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
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
    public String name() {
        return name;
    }

    @Override
    public String simpleName() {
        return name;
    }

    @Override
    public String fullyQualifiedName() {
        return fullyQualifiedName;
    }

    @Override
    public String nameInLinkedAnnotation() {
        return owner.name + ":" + name;
    }

    public boolean hasBeenInspected() {
        return parameterInspection.isSet();
    }

    /*
    the reason we want all three fields (index, name, owner) in the equality, where name or index seems redundant,
    is that translations which change name and swap parameters should not pick up the wrong variable.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterInfo that = (ParameterInfo) o;
        return index == that.index
                && name.equals(that.name)
                && owner.equals(that.owner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, name, owner);
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
            if (parameterAnalysis.isSet() && owner.computedAnalysis()) {
                outputBuilder.add(peekIntoAnnotations(annotation, annotationsSeen, parameterAnalysis.get()));
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


    private OutputBuilder peekIntoAnnotations(AnnotationExpression annotation, Set<TypeInfo> annotationsSeen, ParameterAnalysis parameterAnalysis) {
        AnnotationParameters parameters = annotation.e2ImmuAnnotationParameters();
        OutputBuilder outputBuilder = new OutputBuilder();
        if (parameters != null) {
            if (!parameters.contract()) {
                Analysis.AnnotationCheck annotationCheck = parameterAnalysis.getAnnotation(annotation);
                outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT)
                        .add(new Text(annotationCheck.toString()))
                        .add(Symbol.RIGHT_BLOCK_COMMENT);
                if (annotationCheck.hasBeenComputed()) {
                    // no need to add our computed value
                    annotationsSeen.add(annotation.typeInfo());
                }
            } else {
                // contract, not absent -> no need to add our computed value
                if (!parameters.absent()) annotationsSeen.add(annotation.typeInfo());
            }
        }
        return outputBuilder;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return typesReferenced(false);
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        UpgradableBooleanMap<TypeInfo> inspectedAnnotations =
                parameterInspection.get().getAnnotations().stream()
                        .flatMap(ae -> ae.typesReferenced().stream())
                        .collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> analysedAnnotations = hasBeenAnalysed()
                ? parameterAnalysis.get().getAnnotationStream()
                .flatMap(ae -> ae.getKey().typesReferenced().stream())
                .collect(UpgradableBooleanMap.collector())
                : UpgradableBooleanMap.of();

        return UpgradableBooleanMap.of(parameterizedType.typesReferenced(explicit), analysedAnnotations, inspectedAnnotations);
    }

    public static final int PARAMETER_WEIGHT = 100;

    @Override
    public UpgradableIntMap<TypeInfo> typesReferenced2() {
        UpgradableIntMap<TypeInfo> inspectedAnnotations =
                parameterInspection.get().getAnnotations().stream()
                        .flatMap(ae -> ae.typesReferenced2(TypeInfo.ANNOTATION_WEIGHT).stream())
                        .collect(UpgradableIntMap.collector());
        UpgradableIntMap<TypeInfo> analysedAnnotations = hasBeenAnalysed()
                ? parameterAnalysis.get().getAnnotationStream()
                .flatMap(ae -> ae.getKey().typesReferenced2(TypeInfo.ANNOTATION_WEIGHT).stream())
                .collect(UpgradableIntMap.collector())
                : UpgradableIntMap.of();
        return UpgradableIntMap.of(parameterizedType.typesReferenced2(PARAMETER_WEIGHT), analysedAnnotations,
                inspectedAnnotations);
    }

    // as variable
    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new QualifiedName(name, null, QualifiedName.Required.NEVER));
    }

    @Override
    public int compareTo(Variable v) {
        if (v instanceof ParameterInfo o) {
            if (owner == o.owner) {
                return index - o.index;
            }
            return owner.fullyQualifiedName.compareTo(o.owner.fullyQualifiedName);
        }
        return fullyQualifiedName.compareTo(v.fullyQualifiedName());
    }

    @Override
    public MethodInfo getMethod() {
        return owner;
    }

    @Override
    public String niceClassName() {
        return "Parameter";
    }

    @Override
    public Location newLocation() {
        return new LocationImpl(this);
    }

    @Override
    public CausesOfDelay delay(CauseOfDelay.Cause cause) {
        return DelayFactory.createDelay(newLocation(), cause);
    }

    @Override
    public int getComplexity() {
        return 2;
    }
}
