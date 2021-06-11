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

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.SetOnce;

import java.util.*;

@Container
@E2Immutable(after = "TypeAnalyser.analyse()") // and not MethodAnalyser.analyse(), given the back reference
public class MethodInfo implements WithInspectionAndAnalysis {

    public final TypeInfo typeInfo; // back reference, only @ContextClass after...
    public final String name;
    public final String fullyQualifiedName;
    public final String distinguishingName;
    public final boolean isConstructor;

    public final SetOnce<MethodInspection> methodInspection = new SetOnce<>();
    public final SetOnce<MethodAnalysis> methodAnalysis = new SetOnce<>();
    public final SetOnce<MethodResolution> methodResolution = new SetOnce<>();

    // for constructors
    public MethodInfo(@NotNull TypeInfo typeInfo, String fullyQualifiedName, String distinguishingName) {
        this(typeInfo, dropDollar(typeInfo.simpleName), fullyQualifiedName, distinguishingName, true);
    }

    // for methods
    public MethodInfo(@NotNull TypeInfo typeInfo, @NotNull String name, String fullyQualifiedName, String distinguishingName) {
        this(typeInfo, dropDollarGetClass(name), fullyQualifiedName, distinguishingName, false);
    }

    public static String dropDollarGetClass(String string) {
        if (string.endsWith("$")) {
            if (!"getClass$".equals(string)) {
                throw new UnsupportedOperationException();
            }
            return "getClass";
        }
        return string;
    }

    public static String dropDollar(String string) {
        if (string.endsWith("$")) return string.substring(0, string.length() - 1);
        return string;
    }

    /**
     * it is possible to observe a method without being able to see its return type. That does not make
     * the method a constructor... we cannot use the returnTypeObserved == null as isConstructor
     */
    public MethodInfo(@NotNull TypeInfo typeInfo, @NotNull String name, String fullyQualifiedName,
                      String distinguishingName, boolean isConstructor) {
        Objects.requireNonNull(typeInfo);
        Objects.requireNonNull(name);
        Objects.requireNonNull(fullyQualifiedName);
        Objects.requireNonNull(distinguishingName);
        this.typeInfo = typeInfo;
        this.name = name;
        this.fullyQualifiedName = fullyQualifiedName;
        this.distinguishingName = distinguishingName;
        this.isConstructor = isConstructor;
    }

    public boolean hasBeenInspected() {
        return methodInspection.isSet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodInfo that = (MethodInfo) o;
        return fullyQualifiedName.equals(that.fullyQualifiedName);
    }

    @Override
    public int hashCode() {
        return fullyQualifiedName.hashCode();
    }

    @Override
    public Inspection getInspection() {
        return methodInspection.get();
    }

    @Override
    public void setAnalysis(Analysis analysis) {
        if (analysis instanceof MethodAnalysis ma) {
            methodAnalysis.set(ma);
            Iterator<ParameterAnalysis> it = ma.getParameterAnalyses().iterator();
            for (ParameterInfo parameterInfo : methodInspection.get().getParameters()) {
                if (!it.hasNext()) throw new UnsupportedOperationException();
                ParameterAnalysis parameterAnalysis = it.next();
                parameterInfo.setAnalysis(parameterAnalysis);
            }
        } else throw new UnsupportedOperationException();
    }

    @Override
    public Analysis getAnalysis() {
        return methodAnalysis.get();
    }

    @Override
    public boolean hasBeenAnalysed() {
        return methodAnalysis.isSet();
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        if (!hasBeenInspected()) return UpgradableBooleanMap.of();
        MethodInspection inspection = methodInspection.get();
        UpgradableBooleanMap<TypeInfo> constructorTypes = isConstructor ? UpgradableBooleanMap.of() :
                inspection.getReturnType().typesReferenced(true);
        UpgradableBooleanMap<TypeInfo> parameterTypes =
                inspection.getParameters().stream()
                        .flatMap(p -> p.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> annotationTypes =
                inspection.getAnnotations().stream().flatMap(ae -> ae.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> analysedAnnotationTypes =
                hasBeenAnalysed() ? methodAnalysis.get().getAnnotationStream()
                        .filter(e -> e.getValue().isVisible())
                        .flatMap(e -> e.getKey().typesReferenced().stream())
                        .collect(UpgradableBooleanMap.collector()) : UpgradableBooleanMap.of();
        UpgradableBooleanMap<TypeInfo> exceptionTypes =
                inspection.getExceptionTypes().stream().flatMap(et -> et.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> bodyTypes = hasBeenInspected() ?
                inspection.getMethodBody().typesReferenced() : UpgradableBooleanMap.of();
        UpgradableBooleanMap<TypeInfo> companionMethodTypes = inspection.getCompanionMethods().values().stream()
                .flatMap(cm -> cm.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());
        return UpgradableBooleanMap.of(constructorTypes, parameterTypes, analysedAnnotationTypes,
                annotationTypes, exceptionTypes, companionMethodTypes, bodyTypes);
    }

    @Override
    public TypeInfo primaryType() {
        return typeInfo.primaryType();
    }

    public OutputBuilder output(Qualification qualification) {
        return output(qualification, AnalyserContext.NULL_IF_NOT_SET);
    }

    public OutputBuilder output(Qualification qualification, AnalysisProvider analysisProvider) {
        return OutputMethodInfo.output(this, qualification, analysisProvider);
    }

    public String fullyQualifiedName() {
        return fullyQualifiedName;
    }

    public String distinguishingName() {
        return distinguishingName;
    }

    public ParameterizedType returnType() {
        return methodInspection.get().getReturnType();
    }

    public Optional<AnnotationExpression> hasInspectedAnnotation(String annotationFQN) {
        if (!hasBeenInspected()) return Optional.empty();
        Optional<AnnotationExpression> fromMethod = (getInspection().getAnnotations().stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(annotationFQN))).findFirst();
        if (fromMethod.isPresent()) return fromMethod;
        if (methodInspection.isSet()) {
            for (MethodInfo interfaceMethod : methodInspection.get().getImplementationOf()) {
                Optional<AnnotationExpression> fromInterface = (interfaceMethod.hasInspectedAnnotation(annotationFQN));
                if (fromInterface.isPresent()) return fromInterface;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<AnnotationExpression> hasInspectedAnnotation(Class<?> annotation) {
        String annotationFQN = annotation.getName();
        return hasInspectedAnnotation(annotationFQN);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return fullyQualifiedName();
    }

    public boolean isNotOverridingAnyOtherMethod() {
        return methodResolution.get().overrides().isEmpty();
    }

    public boolean isPrivate() {
        return methodInspection.get().getModifiers().contains(MethodModifier.PRIVATE);
    }

    public boolean isPrivate(InspectionProvider inspectionProvider) {
        return inspectionProvider.getMethodInspection(this).getModifiers().contains(MethodModifier.PRIVATE);
    }

    public boolean isVoid() {
        return Primitives.isVoidOrJavaLangVoid(returnType());
    }

    public boolean isSynchronized() {
        return methodInspection.get().getModifiers().contains(MethodModifier.SYNCHRONIZED);
    }

    public boolean isAbstract() {
        if (typeInfo.typeInspection.get().isInterface()) {
            return !methodInspection.get().getModifiers().contains(MethodModifier.DEFAULT);
        }
        return methodInspection.get().getModifiers().contains(MethodModifier.ABSTRACT);
    }

    public boolean isSingleAbstractMethod() {
        MethodInspection inspection = methodInspection.get();
        return typeInfo.typeInspection.get().isFunctionalInterface() &&
                !inspection.isStatic() && !inspection.isDefault();
    }

    public boolean isNotATestMethod() {
        return hasInspectedAnnotation("org.junit.Test").isEmpty() &&
                hasInspectedAnnotation("org.junit.jupiter.api.Test").isEmpty();
    }

    public boolean noReturnValue() {
        return isVoid() || isConstructor;
    }

    public boolean hasReturnValue() {
        return !noReturnValue();
    }

    /**
     * Even when we're analysing types, we may skip this method
     *
     * @return true when we can skip the analysers
     */
    public boolean shallowAnalysis() {
        return methodInspection.isSet() && methodInspection.get().getMethodBody() == Block.EMPTY_BLOCK;
    }

    public boolean hasStatements() {
        return methodInspection.get().getMethodBody().structure.statements().size() > 0;
    }

    public boolean partOfCallCycle() {
        Set<MethodInfo> reached = methodResolution.get("Method " + fullyQualifiedName).methodsOfOwnClassReached();
        return reached.size() > 1 && reached.contains(this);
    }

    public boolean isCompanionMethod() {
        return CompanionMethodName.extract(name) != null;
    }

    @Override
    public MethodInfo getMethod() {
        return this;
    }

    @Override
    public String niceClassName() {
        return "Method";
    }
}
