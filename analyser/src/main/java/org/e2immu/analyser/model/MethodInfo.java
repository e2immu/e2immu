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

import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.SwitchStatement;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
                hasBeenAnalysed()? methodAnalysis.get().getAnnotationStream()
                        .filter(e -> e.getValue().isVisible())
                        .flatMap(e -> e.getKey().typesReferenced().stream())
                        .collect(UpgradableBooleanMap.collector()): UpgradableBooleanMap.of();
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

    // IMPORTANT: do not write the first MID to methodGG, because that one is written by the joiner
    public OutputBuilder output(Guide.GuideGenerator methodGG) {
        OutputBuilder mainAndCompanions = new OutputBuilder();
        MethodInspection inspection = methodInspection.get();

        boolean nonEmpty = outputCompanions(inspection, mainAndCompanions, methodGG);

        OutputBuilder afterAnnotations = new OutputBuilder();

        afterAnnotations.add(Arrays.stream(MethodModifier.sort(inspection.getModifiers()))
                .map(mod -> new OutputBuilder().add(new Text(mod)))
                .collect(OutputBuilder.joining(Space.ONE)));
        if (!inspection.getModifiers().isEmpty()) afterAnnotations.add(Space.ONE);

        if (!inspection.getTypeParameters().isEmpty()) {
            afterAnnotations.add(Symbol.LEFT_ANGLE_BRACKET);
            afterAnnotations.add(inspection.getTypeParameters().stream().map(TypeParameter::output).collect(OutputBuilder.joining(Symbol.COMMA)));
            afterAnnotations.add(Symbol.RIGHT_ANGLE_BRACKET).add(Space.ONE);
        }

        if (!isConstructor) {
            afterAnnotations.add(inspection.getReturnType().output()).add(Space.ONE);
        }
        afterAnnotations.add(new Text(name));
        if (inspection.getParameters().isEmpty()) {
            afterAnnotations.add(Symbol.OPEN_CLOSE_PARENTHESIS);
        } else {
            afterAnnotations.add(inspection.getParameters().stream()
                    .map(ParameterInfo::outputDeclaration)
                    .collect(OutputBuilder.joining(Symbol.COMMA, Symbol.LEFT_PARENTHESIS, Symbol.RIGHT_PARENTHESIS,
                            Guide.generatorForParameterDeclaration())));
        }
        if (!inspection.getExceptionTypes().isEmpty()) {
            afterAnnotations.add(Space.ONE_REQUIRED_EASY_SPLIT).add(new Text("throws")).add(Space.ONE)
                    .add(inspection.getExceptionTypes().stream()
                            .map(ParameterizedType::output).collect(OutputBuilder.joining(Symbol.COMMA)));
        }
        if (hasBeenInspected()) {
            StatementAnalysis firstStatement = methodAnalysis.isSet() ? methodAnalysis.get().getFirstStatement() : null;
            afterAnnotations.add(inspection.getMethodBody().output(firstStatement));
        } else {
            afterAnnotations.add(Space.ONE).add(Symbol.LEFT_BRACE).add(Symbol.RIGHT_BRACE);
        }

        Stream<OutputBuilder> annotationStream = buildAnnotationOutput();
        OutputBuilder mainMethod = Stream.concat(annotationStream, Stream.of(afterAnnotations))
                .collect(OutputBuilder.joining(Space.ONE_REQUIRED_EASY_SPLIT, Guide.generatorForAnnotationList()));

        if (nonEmpty) mainAndCompanions.add(methodGG.mid());
        return mainAndCompanions.add(mainMethod);
    }

    private boolean outputCompanions(MethodInspection methodInspection, OutputBuilder outputBuilder, Guide.GuideGenerator methodGG) {
        methodInspection.getCompanionMethods().values().forEach(companion -> outputBuilder.add(companion.output(methodGG)));
        boolean nonEmpty = !methodInspection.getCompanionMethods().isEmpty();
        if (methodAnalysis.isSet()) {
            methodAnalysis.get().getComputedCompanions().values().forEach(companion -> outputBuilder.add(companion.output(methodGG)));
            nonEmpty |= !methodAnalysis.get().getComputedCompanions().isEmpty();
        }
        return nonEmpty;
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

    public int atLeastOneParameterModified() {
        return methodInspection.get().getParameters().stream()
                .mapToInt(parameterInfo -> parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED))
                .max().orElse(Level.FALSE);
    }

    @Override
    public String toString() {
        return fullyQualifiedName();
    }

    public boolean isNotOverridingAnyOtherMethod() {
        return methodResolution.get().overrides().isEmpty();
    }

    public boolean cannotBeOverridden() {
        MethodInspection inspection = methodInspection.get();
        return inspection.isStatic() ||
                inspection.getModifiers().contains(MethodModifier.FINAL)
                || inspection.getModifiers().contains(MethodModifier.PRIVATE)
                || typeInfo.typeInspection.get().modifiers().contains(TypeModifier.FINAL);
    }

    public boolean isPrivate() {
        return methodInspection.get().getModifiers().contains(MethodModifier.PRIVATE);
    }

    public boolean isVoid() {
        return Primitives.isVoid(returnType());
    }

    public boolean isSynchronized() {
        return methodInspection.get().getModifiers().contains(MethodModifier.SYNCHRONIZED);
    }

    public boolean isAbstract() {
        if(typeInfo.typeInspection.get().isInterface()) {
            return !methodInspection.get().getModifiers().contains(MethodModifier.DEFAULT);
        }
        return methodInspection.get().getModifiers().contains(MethodModifier.ABSTRACT);
    }

    public boolean isSingleAbstractMethod() {
        MethodInspection inspection = methodInspection.get();
        return typeInfo.typeInspection.get().isFunctionalInterface() &&
                !inspection.isStatic() && !inspection.isDefault();
    }

    public Set<ParameterizedType> explicitTypes() {
        return explicitTypes(methodInspection.get().getMethodBody());
    }

    public static Set<ParameterizedType> explicitTypes(Element start) {
        Set<ParameterizedType> result = new HashSet<>();
        Consumer<Element> visitor = element -> {

            // a.method() -> type of a cannot be replaced by unbound type parameter
            if (element instanceof MethodCall mc) {
                result.add(mc.object.returnType());
                addTypesFromParameters(result, mc.methodInfo);
            }

            // new A() -> A cannot be replaced by unbound type parameter
            if (element instanceof NewObject newObject) {
                result.add(newObject.parameterizedType());
                if (newObject.constructor() != null) { // can be null, anonymous implementation of interface
                    addTypesFromParameters(result, newObject.constructor());
                }
            }

            // a.b -> type of a cannot be replaced by unbound type parameter
            if (element instanceof FieldAccess fieldAccess) {
                result.add(fieldAccess.expression().returnType());
            }

            // for(E e: list) -> type of list cannot be replaced by unbound type parameter
            if (element instanceof ForEachStatement forEach) {
                result.add(forEach.expression.returnType());
            }

            // switch(e) -> type of e cannot be replaced
            if (element instanceof SwitchStatement switchStatement) {
                result.add(switchStatement.expression.returnType());
            }
        };
        start.visit(visitor);
        return result;
    }

    // a.method(b, c) -> unless the formal parameter types are either Object or another unbound parameter type,
    // they cannot be replaced by unbound type parameter
    private static void addTypesFromParameters(Set<ParameterizedType> result, MethodInfo methodInfo) {
        Objects.requireNonNull(methodInfo);
        for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().getParameters()) {
            ParameterizedType formal = parameterInfo.parameterizedType;
            if (!Primitives.isJavaLangObject(formal) && !formal.isUnboundParameterType()) {
                result.add(formal);
            }
        }
    }

    public boolean isTestMethod() {
        return hasInspectedAnnotation("org.junit.Test").isPresent();
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
        return methodInspection.get().getMethodBody() == Block.EMPTY_BLOCK;
    }

    public boolean hasStatements() {
        return methodInspection.get().getMethodBody().structure.statements().size() > 0;
    }
}
