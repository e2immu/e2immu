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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.SwitchStatement;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        methodAnalysis.set((MethodAnalysis) analysis);
    }


    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        if (!hasBeenInspected()) return UpgradableBooleanMap.of();
        UpgradableBooleanMap<TypeInfo> constructorTypes = isConstructor ? UpgradableBooleanMap.of() :
                methodInspection.get().getReturnType().typesReferenced(true);
        UpgradableBooleanMap<TypeInfo> parameterTypes =
                methodInspection.get().getParameters().stream()
                        .flatMap(p -> p.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> annotationTypes =
                methodInspection.get().getAnnotations().stream().flatMap(ae -> ae.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> exceptionTypes =
                methodInspection.get().getExceptionTypes().stream().flatMap(et -> et.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> bodyTypes = hasBeenInspected() ?
                methodInspection.get().getMethodBody().typesReferenced() : UpgradableBooleanMap.of();
        UpgradableBooleanMap<TypeInfo> companionMethodTypes = methodInspection.get().getCompanionMethods().values().stream()
                .flatMap(cm -> cm.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());
        return UpgradableBooleanMap.of(constructorTypes, parameterTypes, annotationTypes, exceptionTypes, companionMethodTypes, bodyTypes);
    }

    @Override
    public TypeInfo primaryType() {
        return typeInfo.primaryType();
    }

    public String stream(int indent) {
        StringBuilder sb = new StringBuilder();
        MethodInspection inspection = methodInspection.get();

        Set<TypeInfo> annotationsSeen = new HashSet<>();
        for (AnnotationExpression annotation : inspection.getAnnotations()) {
            StringUtil.indent(sb, indent);
            sb.append(annotation.stream());
            if (methodAnalysis.isSet()) {
                methodAnalysis.get().peekIntoAnnotations(annotation, annotationsSeen, sb);
            }
            sb.append("\n");
        }
        if (methodAnalysis.isSet()) {
            methodAnalysis.get().getAnnotationStream().forEach(entry -> {
                boolean present = entry.getValue();
                AnnotationExpression annotation = entry.getKey();
                if (present && !annotationsSeen.contains(annotation.typeInfo())) {
                    StringUtil.indent(sb, indent);
                    sb.append(annotation.stream());
                    sb.append("\n");
                }
            });
        }

        StringUtil.indent(sb, indent);
        sb.append(inspection.getModifiers().stream().map(m -> m.toJava() + " ").collect(Collectors.joining()));
        MethodInspection methodInspection = this.methodInspection.get();
        if (methodInspection.isStatic()) {
            sb.append("static ");
        }
        if (!methodInspection.getTypeParameters().isEmpty()) {
            sb.append("<");
            sb.append(methodInspection.getTypeParameters().stream().map(TypeParameter::getName).collect(Collectors.joining(", ")));
            sb.append("> ");
        }

        if (!isConstructor) {
            sb.append(inspection.getReturnType().stream());
            sb.append(" ");
        }
        sb.append(name);
        sb.append("(");

        sb.append(inspection.getParameters().stream().map(ParameterInfo::stream).collect(Collectors.joining(", ")));
        sb.append(")");
        if (!inspection.getExceptionTypes().isEmpty()) {
            sb.append(" throws ");
            sb.append(inspection.getExceptionTypes().stream()
                    .map(ParameterizedType::stream).collect(Collectors.joining(", ")));
        }
        if (hasBeenInspected()) {
            if (methodAnalysis.isSet() && methodAnalysis.get().getFirstStatement() != null) {
                sb.append(inspection.getMethodBody().statementString(indent, methodAnalysis.get().getFirstStatement()));
            } else {
                sb.append(inspection.getMethodBody().statementString(indent, null));
            }
        } else {
            sb.append(" { }");
        }
        return sb.toString();
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

    // given R accept(T t), and types={string}, returnType=string, deduce that R=string, T=string, and we have Function<String, String>
    public List<ParameterizedType> typeParametersComputed(List<ParameterizedType> types, ParameterizedType inferredReturnType) {
        if (typeInfo.typeInspection.get().typeParameters().isEmpty()) return List.of();
        return typeInfo.typeInspection.get().typeParameters().stream().map(typeParameter -> {
            int cnt = 0;
            for (ParameterInfo parameterInfo : methodInspection.get().getParameters()) {
                if (parameterInfo.parameterizedType.typeParameter == typeParameter) {
                    return types.get(cnt); // this is one we know!
                }
                cnt++;
            }
            if (methodInspection.get().getReturnType().typeParameter == typeParameter) return inferredReturnType;
            return new ParameterizedType(typeParameter, 0, ParameterizedType.WildCard.NONE);
        }).collect(Collectors.toList());
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

    public boolean sameMethod(MethodInfo target, Map<NamedType, ParameterizedType> translationMap) {
        return name.equals(target.name) &&
                sameParameters(methodInspection.get().getParameters(), target.methodInspection.get().getParameters(), translationMap);
    }

    private static boolean sameParameters(List<ParameterInfo> parametersOfMyMethod,
                                          List<ParameterInfo> parametersOfTarget,
                                          Map<NamedType, ParameterizedType> translationMap) {
        if (parametersOfMyMethod.size() != parametersOfTarget.size()) return false;
        int i = 0;
        for (ParameterInfo parameterInfo : parametersOfMyMethod) {
            ParameterInfo p2 = parametersOfTarget.get(i);
            if (differentType(parameterInfo.parameterizedType, p2.parameterizedType, translationMap)) return false;
            i++;
        }
        return true;
    }

    /**
     * This method is NOT the same as <code>isAssignableFrom</code>, and it serves a different purpose.
     * We need to take care to ensure that overloads are different.
     * <p>
     * java.lang.Appendable.append(java.lang.CharSequence) and java.lang.AbstractStringBuilder.append(java.lang.String)
     * can exist together in one class. They are different, even if String is assignable to CharSequence.
     * <p>
     * On the other hand, int comparable(Value other) is the same method as int comparable(T) in Comparable.
     * This is solved by taking the concrete type when we move from concrete types to parameterized types.
     *
     * @param inSuperType    first type
     * @param inSubType      second type
     * @param translationMap a map from type parameters in the super type to (more) concrete types in the sub-type
     * @return true if the types are "different"
     */
    private static boolean differentType(ParameterizedType inSuperType,
                                         ParameterizedType inSubType,
                                         Map<NamedType, ParameterizedType> translationMap) {
        Objects.requireNonNull(inSuperType);
        Objects.requireNonNull(inSubType);
        if (inSuperType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR && inSubType == inSuperType) return false;

        if (inSuperType.typeInfo != null) {
            if (inSubType.typeInfo != inSuperType.typeInfo) return true;
            if (inSuperType.parameters.size() != inSubType.parameters.size()) return true;
            int i = 0;
            for (ParameterizedType param1 : inSuperType.parameters) {
                ParameterizedType param2 = inSubType.parameters.get(i);
                if (differentType(param1, param2, translationMap)) return true;
                i++;
            }
            return false;
        }
        if (inSuperType.typeParameter != null && inSubType.typeInfo != null) {
            // check if we can go from the parameter to the concrete type
            ParameterizedType inMap = translationMap.get(inSuperType.typeParameter);
            if (inMap == null) return true;
            return differentType(inMap, inSubType, translationMap);
        }
        if (inSuperType.typeParameter == null && inSubType.typeParameter == null) return false;
        if (inSuperType.typeParameter == null || inSubType.typeParameter == null) return true;
        // they CAN have different indices, example in BiFunction TestTestExamplesWithAnnotatedAPIs, AnnotationsOnLambdas
        ParameterizedType translated =
                translationMap.get(inSuperType.typeParameter);
        if (translated != null && translated.typeParameter == inSubType.typeParameter) return false;
        if (inSubType.isUnboundParameterType() && inSuperType.isUnboundParameterType()) return false;
        List<ParameterizedType> inSubTypeBounds = inSubType.typeParameter.getTypeBounds();
        List<ParameterizedType> inSuperTypeBounds = inSuperType.typeParameter.getTypeBounds();
        if (inSubTypeBounds.size() != inSuperTypeBounds.size()) return true;
        int i = 0;
        for (ParameterizedType typeBound : inSubType.typeParameter.getTypeBounds()) {
            boolean different = differentType(typeBound, inSuperTypeBounds.get(i), translationMap);
            if (different) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return fullyQualifiedName();
    }

    public boolean isVarargs() {
        MethodInspection mi = methodInspection.get();
        if (mi.getParameters().isEmpty()) return false;
        return mi.getParameters().get(mi.getParameters().size() - 1).parameterInspection.get().isVarArgs();
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
                result.add(mc.computedScope.returnType());
                addTypesFromParameters(result, mc.methodInfo);
            }

            // new A() -> A cannot be replaced by unbound type parameter
            if (element instanceof NewObject newObject) {
                result.add(newObject.parameterizedType);
                if (newObject.constructor != null) { // can be null, anonymous implementation of interface
                    addTypesFromParameters(result, newObject.constructor);
                }
            }

            // a.b -> type of a cannot be replaced by unbound type parameter
            if (element instanceof FieldAccess fieldAccess) {
                result.add(fieldAccess.expression.returnType());
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
        return methodInspection.get().getMethodBody().structure.statements.size() > 0;
    }
}
