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
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.SwitchStatement;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetTwice;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Container
@E2Immutable(after = "TypeAnalyser.analyse()") // and not MethodAnalyser.analyse(), given the back reference
public class MethodInfo implements WithInspectionAndAnalysis {

    public final TypeInfo typeInfo; // back reference, only @ContextClass after...
    public final String name;
    public final List<ParameterInfo> parametersAsObserved;
    public final ParameterizedType returnTypeObserved; // @ContextClass
    public final boolean isConstructor;
    public final boolean isStatic;
    public final boolean isDefaultImplementation;

    public final SetTwice<MethodInspection> methodInspection = new SetTwice<>();
    public final SetOnce<MethodAnalysis> methodAnalysis = new SetOnce<>();
    public final SetOnce<MethodResolution> methodResolution = new SetOnce<>();

    // for constructors
    public MethodInfo(@NotNull TypeInfo typeInfo, @NotNull List<ParameterInfo> parametersAsObserved) {
        this(typeInfo, dropDollar(typeInfo.simpleName), parametersAsObserved, null, true, false, false);
    }

    public MethodInfo(@NotNull TypeInfo typeInfo, @NotNull String name, @NotNull List<ParameterInfo> parametersAsObserved,
                      ParameterizedType returnTypeObserved, boolean isStatic) {
        this(typeInfo, dropDollarGetClass(name), parametersAsObserved, returnTypeObserved, false, isStatic, false);
    }

    public MethodInfo(@NotNull TypeInfo typeInfo, @NotNull String name, @NotNull List<ParameterInfo> parametersAsObserved,
                      ParameterizedType returnTypeObserved, boolean isStatic, boolean isDefaultImplementation) {
        this(typeInfo, dropDollarGetClass(name), parametersAsObserved, returnTypeObserved, false, isStatic, isDefaultImplementation);
    }

    public MethodInfo(@NotNull TypeInfo typeInfo, @NotNull String name, boolean isStatic) {
        this(typeInfo, dropDollarGetClass(name), List.of(), null, false, isStatic, false);
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
    private MethodInfo(@NotNull TypeInfo typeInfo, @NotNull String name, @NotNull List<ParameterInfo> parametersAsObserved,
                       ParameterizedType returnTypeObserved, boolean isConstructor, boolean isStatic, boolean isDefaultImplementation) {
        Objects.requireNonNull(typeInfo, "Trying to create a method " + name + " but null type");
        Objects.requireNonNull(name);
        Objects.requireNonNull(parametersAsObserved);

        if (isStatic && isConstructor) throw new IllegalArgumentException();
        this.isStatic = isStatic;
        this.typeInfo = typeInfo;
        this.name = name;
        this.parametersAsObserved = parametersAsObserved;
        this.returnTypeObserved = returnTypeObserved;
        this.isConstructor = isConstructor;
        this.isDefaultImplementation = isDefaultImplementation;
        if (isConstructor && returnTypeObserved != null) throw new IllegalArgumentException();
    }

    public boolean hasBeenInspected() {
        return methodInspection.isSet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodInfo that = (MethodInfo) o;
        if (hasBeenInspected()) {
            return methodInspection.get().getFullyQualifiedName().equals(that.methodInspection.get().getFullyQualifiedName());
        }
        return typeInfo.equals(that.typeInfo) &&
                name.equals(that.name) &&
                parametersAsObserved.equals(that.parametersAsObserved);
    }

    @Override
    public int hashCode() {
        if (hasBeenInspected()) {
            return Objects.hash(methodInspection.get().getFullyQualifiedName());
        }
        return Objects.hash(typeInfo, name, parametersAsObserved);
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
        UpgradableBooleanMap<TypeInfo> constructorTypes = isConstructor ? UpgradableBooleanMap.of() : hasBeenInspected() ?
                methodInspection.get().getReturnType().typesReferenced(true) : returnTypeObserved.typesReferenced(true);
        UpgradableBooleanMap<TypeInfo> parameterTypes =
                (hasBeenInspected() ? methodInspection.get().getParameters() : parametersAsObserved)
                        .stream().flatMap(p -> p.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> annotationTypes = hasBeenInspected() ?
                methodInspection.get().getAnnotations().stream().flatMap(ae -> ae.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()) :
                UpgradableBooleanMap.of();
        UpgradableBooleanMap<TypeInfo> exceptionTypes = hasBeenInspected() ?
                methodInspection.get().getExceptionTypes().stream().flatMap(et -> et.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector()) :
                UpgradableBooleanMap.of();
        UpgradableBooleanMap<TypeInfo> bodyTypes = hasBeenInspected() ?
                methodInspection.get().getMethodBody().typesReferenced() : UpgradableBooleanMap.of();
        UpgradableBooleanMap<TypeInfo> companionMethodTypes = hasBeenInspected() ? methodInspection.get().getCompanionMethods().values().stream()
                .flatMap(cm -> cm.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()) : UpgradableBooleanMap.of();
        return UpgradableBooleanMap.of(constructorTypes, parameterTypes, annotationTypes, exceptionTypes, companionMethodTypes, bodyTypes);
    }

    @Override
    public TypeInfo primaryType() {
        return typeInfo.primaryType();
    }

    public String stream(int indent) {
        StringBuilder sb = new StringBuilder();
        ParameterizedType returnType;
        if (hasBeenInspected()) {
            returnType = isConstructor ? null : methodInspection.get().getReturnType();
        } else {
            returnType = returnTypeObserved;
        }

        List<MethodModifier> methodModifiers;
        if (hasBeenInspected()) {
            methodModifiers = methodInspection.get().getModifiers();
        } else {
            methodModifiers = List.of(MethodModifier.PUBLIC);
        }
        if (hasBeenInspected()) {
            Set<TypeInfo> annotationsSeen = new HashSet<>();
            for (AnnotationExpression annotation : methodInspection.get().getAnnotations()) {
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
        }
        StringUtil.indent(sb, indent);
        sb.append(methodModifiers.stream().map(m -> m.toJava() + " ").collect(Collectors.joining()));
        if (isStatic) {
            sb.append("static ");
        }
        if (hasBeenInspected()) {
            MethodInspection methodInspection = this.methodInspection.get();
            if (!methodInspection.getTypeParameters().isEmpty()) {
                sb.append("<");
                sb.append(methodInspection.getTypeParameters().stream().map(tp -> tp.name).collect(Collectors.joining(", ")));
                sb.append("> ");
            }
        }
        if (!isConstructor) {
            sb.append(returnType.stream());
            sb.append(" ");
        }
        sb.append(name);
        sb.append("(");

        List<ParameterInfo> parameters;
        if (hasBeenInspected()) {
            parameters = methodInspection.get().getParameters();
        } else {
            parameters = parametersAsObserved;
        }
        sb.append(parameters.stream().map(ParameterInfo::stream).collect(Collectors.joining(", ")));
        sb.append(")");
        if (hasBeenInspected() && !methodInspection.get().getExceptionTypes().isEmpty()) {
            sb.append(" throws ");
            sb.append(methodInspection.get().getExceptionTypes().stream()
                    .map(ParameterizedType::stream).collect(Collectors.joining(", ")));
        }
        if (hasBeenInspected()) {
            if (methodAnalysis.isSet() && methodAnalysis.get().getFirstStatement() != null) {
                sb.append(methodInspection.get().getMethodBody().statementString(indent, methodAnalysis.get().getFirstStatement()));
            } else {
                sb.append(methodInspection.get().getMethodBody().statementString(indent, null));
            }
        } else {
            sb.append(" { }");
        }
        return sb.toString();
    }

    public String fullyQualifiedName() {
        if (methodInspection.isSet()) {
            return methodInspection.get().getFullyQualifiedName();
        }
        return typeInfo.fullyQualifiedName + "." + name + "()";
    }

    public String distinguishingName() {
        if (methodInspection.isSet()) {
            return methodInspection.get().getDistinguishingName();
        }
        return typeInfo.fullyQualifiedName + "." + name + "()";
    }

    public ParameterizedType returnType() {
        return Objects.requireNonNull(hasBeenInspected() ? methodInspection.get().getReturnType() :
                returnTypeObserved, "Null return type for " + fullyQualifiedName() + ", inspected? " + hasBeenInspected());
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
        if (inSubType.typeParameter.typeParameterInspection.isSet() && inSuperType.typeParameter.typeParameterInspection.isSet()) {
            List<ParameterizedType> inSubTypeBounds = inSubType.typeParameter.typeParameterInspection.get().typeBounds;
            List<ParameterizedType> inSuperTypeBounds = inSuperType.typeParameter.typeParameterInspection.get().typeBounds;
            if (inSubTypeBounds.size() != inSuperTypeBounds.size()) return true;
            int i = 0;
            for (ParameterizedType typeBound : inSubType.typeParameter.typeParameterInspection.get().typeBounds) {
                boolean different = differentType(typeBound, inSuperTypeBounds.get(i), translationMap);
                if (different) return true;
            }
            return false;
        }
        throw new UnsupportedOperationException("? type parameter inspections not set");
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
        return typeInfo.overrides(this, true).isEmpty();
    }


    public boolean cannotBeOverridden() {
        return isStatic ||
                methodInspection.get().getModifiers().contains(MethodModifier.FINAL)
                || methodInspection.get().getModifiers().contains(MethodModifier.PRIVATE)
                || typeInfo.typeInspection.get().modifiers().contains(TypeModifier.FINAL);
    }

    public boolean isPrivate() {
        return methodInspection.get().getModifiers().contains(MethodModifier.PRIVATE);
    }

    public boolean isVoid() {
        return Primitives.isVoid(returnType());
    }

    public boolean isCalledFromConstructors() {
        for (MethodInfo other : typeInfo.typeInspection.get().constructors()) {
            if (other.methodResolution.get().methodsOfOwnClassReached.get().contains(this)) {
                return true;
            }
        }
        for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields()) {
            if (fieldInfo.fieldInspection.get().initialiserIsSet()) {
                FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().getInitialiser();
                if (fieldInitialiser.implementationOfSingleAbstractMethod() == null) {
                    // return true when the method is part of the expression
                    AtomicBoolean found = new AtomicBoolean();
                    fieldInitialiser.initialiser().visit(elt -> {
                        if (elt instanceof MethodCall methodCall) {
                            if (methodCall.methodInfo == this ||
                                    methodCall.methodInfo.methodResolution.get().methodsOfOwnClassReached.get().contains(this)) {
                                found.set(true);
                            }
                        }
                    });
                    return found.get();
                }
            }
        }
        return false;
    }

    /**
     * Note that this computation has to contain transitive calls.
     *
     * @return true if there is a non-private method in this class which calls this private method.
     */
    public boolean isCalledFromNonPrivateMethod() {
        for (MethodInfo other : typeInfo.typeInspection.get().methods()) {
            if (!other.isPrivate() && other.methodResolution.get().methodsOfOwnClassReached.get().contains(this)) {
                return true;
            }
        }
        for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields()) {
            if (!fieldInfo.isPrivate() && fieldInfo.fieldInspection.get().initialiserIsSet()) {
                FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().getInitialiser();
                if (fieldInitialiser.implementationOfSingleAbstractMethod() != null &&
                        fieldInitialiser.implementationOfSingleAbstractMethod().methodResolution.get().methodsOfOwnClassReached.get().contains(this)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isSynchronized() {
        return methodInspection.get().getModifiers().contains(MethodModifier.SYNCHRONIZED);
    }

    public boolean isAbstract() {
        return methodInspection.get().getModifiers().contains(MethodModifier.ABSTRACT);
    }

    public boolean isSingleAbstractMethod() {
        return typeInfo.isFunctionalInterface() && !isStatic && !isDefaultImplementation;
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
