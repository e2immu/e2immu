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

package org.e2immu.analyser.parser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.util.Lazy;
import org.e2immu.annotation.Constant;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.log;

/**
 * Inside a compilation unit, there is a context in which names are known.
 * This context is inherently recursive, dependent on the container.
 */
public class TypeContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeContext.class);

    private final TypeContext parentContext;

    public final TypeStore typeStore;
    public final String packageName; // this one is filled in UNLESS parentContext == null, because that is the root level
    private final List<Message> messages;
    private final List<TypeInfo> importStaticAsterisk;
    private final Map<String, TypeInfo> importStaticMemberToTypeInfo;

    // TODO at some point move the lazy's to typeStore, and use methods here to grab them
    public final Lazy<AnnotationExpression> constant = new Lazy<>(() -> create(Constant.class));
    public final Lazy<AnnotationExpression> container = new Lazy<>(() -> create(Container.class));
    public final Lazy<AnnotationExpression> e1Container = new Lazy<>(() -> create(E1Container.class));
    public final Lazy<AnnotationExpression> e2Container = new Lazy<>(() -> create(E2Container.class));
    public final Lazy<AnnotationExpression> extensionClass = new Lazy<>(() -> create(ExtensionClass.class));
    public final Lazy<AnnotationExpression> e1Immutable = new Lazy<>(() -> create(E1Immutable.class));
    public final Lazy<AnnotationExpression> e2Immutable = new Lazy<>(() -> create(E2Immutable.class));
    public final Lazy<AnnotationExpression> effectivelyFinal = new Lazy<>(() -> create(Final.class));
    public final Lazy<AnnotationExpression> fluent = new Lazy<>(() -> create(Fluent.class));
    public final Lazy<AnnotationExpression> identity = new Lazy<>(() -> create(Identity.class));
    public final Lazy<AnnotationExpression> ignoreModifications = new Lazy<>(() -> create(IgnoreModifications.class));
    public final Lazy<AnnotationExpression> independent = new Lazy<>(() -> create(Independent.class));
    public final Lazy<AnnotationExpression> linked = new Lazy<>(() -> create(Linked.class));
    public final Lazy<AnnotationExpression> mark = new Lazy<>(() -> create(Mark.class));
    public final Lazy<AnnotationExpression> notModified = new Lazy<>(() -> create(NotModified.class));
    public final Lazy<AnnotationExpression> notNull = new Lazy<>(() -> create(NotNull.class));
    public final Lazy<AnnotationExpression> notNull1 = new Lazy<>(() -> create(NotNull1.class));
    public final Lazy<AnnotationExpression> notNull2 = new Lazy<>(() -> create(NotNull2.class));
    public final Lazy<AnnotationExpression> only = new Lazy<>(() -> create(Only.class));
    public final Lazy<AnnotationExpression> output = new Lazy<>(() -> create(Output.class));
    public final Lazy<AnnotationExpression> singleton = new Lazy<>(() -> create(Singleton.class));
    public final Lazy<AnnotationExpression> utilityClass = new Lazy<>(() -> create(UtilityClass.class));

    public TypeContext() {
        typeStore = new MapBasedTypeStore();
        parentContext = null;
        packageName = null;
        messages = new LinkedList<>();
        importStaticAsterisk = new ArrayList<>();
        importStaticMemberToTypeInfo = new HashMap<>();
    }

    public TypeContext(String packageName, @NotNull TypeContext parentContext) {
        this.parentContext = Objects.requireNonNull(parentContext);
        typeStore = parentContext.typeStore;
        importStaticMemberToTypeInfo = new HashMap<>(parentContext.importStaticMemberToTypeInfo);
        importStaticAsterisk = new ArrayList<>(parentContext.importStaticAsterisk);
        messages = List.of();
        this.packageName = packageName;
    }

    public TypeContext(@NotNull TypeContext parentContext) {
        this(parentContext, parentContext.packageName, parentContext.typeStore);
    }

    public TypeContext(@NotNull TypeContext parentContext, TypeStore typeStore) {
        this(parentContext, parentContext.packageName, typeStore);
    }

    public TypeContext(@NotNull TypeContext parentContext, String packageName, TypeStore typeStore) {
        this.parentContext = Objects.requireNonNull(parentContext);
        this.typeStore = typeStore;
        importStaticMemberToTypeInfo = new HashMap<>(parentContext.importStaticMemberToTypeInfo);
        importStaticAsterisk = new ArrayList<>(parentContext.importStaticAsterisk);
        messages = List.of();
        this.packageName = packageName;
    }

    private final Map<String, NamedType> map = new HashMap<>();

    /**
     * used for: Annotation types, ParameterizedTypes (general types)
     *
     * @param name     the simple name to search for
     * @param complain throw an error when the name is unknown
     * @return the NamedType with that name
     */
    public NamedType get(@NotNull String name, boolean complain) {
        NamedType namedType = map.get(name);
        if (namedType == null && parentContext != null) {
            namedType = parentContext.get(name, false);
            if (namedType == null) {
                // in same package
                namedType = typeStore.get(packageName + "." + name);
            }
        }
        if (namedType == null && parentContext == null) {
            if (name.contains(".")) {
                throw new UnsupportedOperationException("We should not get FQNs or Type.SubType here, there's other methods for that");
            }
            namedType = typeStore.get("java.lang." + name);
        }
        if (namedType == null && complain) {
            throw new UnsupportedOperationException("Unknown type in context: " + name);
        }
        return namedType;
    }

    /**
     * create an annotation for a given class, with a type=AnnotationType.COMPUTED parameter
     *
     * @param clazz must have a method called type of Enum type AnnotationType
     * @return an annotation expression
     */
    private AnnotationExpression create(Class<?> clazz) {
        TypeInfo annotationType = getFullyQualified(AnnotationType.class);
        FieldInfo computed = Primitives.PRIMITIVES.annotationTypeComputed;
        FieldReference computedRef = new FieldReference(computed, null);
        FieldAccess computedAccess = new FieldAccess(new TypeExpression(annotationType.asParameterizedType()), computedRef);
        // NOTE: we've added an import statement in TypeInfo.imports() for this...
        return AnnotationExpression.fromAnalyserExpressions(getFullyQualified(clazz),
                List.of(new MemberValuePair("type", computedAccess)));
    }

    public TypeInfo getFullyQualified(Class<?> clazz) {
        return getFullyQualified(clazz.getName(), true);
    }

    public TypeInfo getFullyQualified(String fullyQualifiedName, boolean complain) {
        TypeInfo typeInfo = typeStore.get(fullyQualifiedName);
        if (typeInfo == null && complain) {
            throw new UnsupportedOperationException("Unknown fully qualified name " + fullyQualifiedName);
        }
        return typeInfo;
    }

    public void addToContext(@NotNull NamedType namedType) {
        addToContext(namedType, true);
    }

    public void addToContext(@NotNull NamedType namedType, boolean allowOverwrite) {
        String simpleName = namedType.simpleName();
        if (allowOverwrite || !map.containsKey(simpleName)) {
            map.put(simpleName, namedType);
        }
    }

    @NotNull
    private static MethodInfo emptyConstructor(@NotNull TypeInfo typeInfo) {
        MethodInfo constructor = new MethodInfo(typeInfo, List.of());
        constructor.methodInspection.set(new MethodInspection.MethodInspectionBuilder()
                .addModifier(MethodModifier.PUBLIC)
                .build(constructor));
        return constructor;
    }

    private static List<TypeInfo> extractTypeInfo(ParameterizedType typeOfObject, Map<NamedType, ParameterizedType> typeMap) {
        TypeInfo typeInfo;
        if (typeOfObject.typeInfo == null) {
            if (typeOfObject.typeParameter == null) throw new UnsupportedOperationException();
            ParameterizedType pt = typeMap.get(typeOfObject.typeParameter);
            if (pt == null) {
                // rather than give an exception here, we replace t by the type that it extends, so that we can find those methods
                // in the case that there is no explicit extension/super, we replace it by the implicit Object
                List<ParameterizedType> typeBounds = typeOfObject.typeParameter.typeParameterInspection.get().typeBounds;
                if (!typeBounds.isEmpty()) {
                    return typeBounds.stream().flatMap(bound -> extractTypeInfo(bound, typeMap).stream()).collect(Collectors.toList());
                } else {
                    typeInfo = Primitives.PRIMITIVES.objectTypeInfo;
                }
            } else {
                typeInfo = pt.typeInfo;
            }
        } else {
            typeInfo = typeOfObject.typeInfo;
        }
        if (!typeInfo.hasBeenInspected())
            throw new UnsupportedOperationException("Type " + typeInfo.fullyQualifiedName + " has not been inspected");
        return List.of(typeInfo);
    }

    public void addImportStaticWildcard(TypeInfo typeInfo) {
        importStaticAsterisk.add(typeInfo);
    }

    public void addImportStatic(TypeInfo typeInfo, String member) {
        importStaticMemberToTypeInfo.put(member, typeInfo);
    }

    public Map<String, FieldReference> staticFieldImports() {
        Map<String, FieldReference> map = new HashMap<>();
        for (Map.Entry<String, TypeInfo> entry : importStaticMemberToTypeInfo.entrySet()) {
            TypeInfo typeInfo = entry.getValue();
            String memberName = entry.getKey();
            typeInfo.typeInspection.get().fields.stream()
                    .filter(FieldInfo::isStatic)
                    .filter(f -> f.name.equals(memberName))
                    .findFirst()
                    .ifPresent(fieldInfo -> map.put(memberName, new FieldReference(fieldInfo, null)));
        }
        for (TypeInfo typeInfo : importStaticAsterisk) {
            typeInfo.typeInspection.get().fields.stream()
                    .filter(FieldInfo::isStatic)
                    .forEach(fieldInfo -> map.put(fieldInfo.name, new FieldReference(fieldInfo, null)));
        }
        return map;
    }

    // TODO: this would be a good candidate to make into a non-static inner class, so that it can be made
    // Comparable!

    public static class MethodCandidate {
        public final MethodTypeParameterMap method;
        public final Set<Integer> parameterIndicesOfFunctionalInterfaces;

        public MethodCandidate(MethodTypeParameterMap method, Set<Integer> parameterIndicesOfFunctionalInterfaces) {
            this.parameterIndicesOfFunctionalInterfaces = parameterIndicesOfFunctionalInterfaces;
            this.method = method;
        }
    }

    public static final int IGNORE_PARAMETER_NUMBERS = -1;

    public List<MethodCandidate> resolveConstructor(ParameterizedType typeOfObject,
                                                    int parametersPresented,
                                                    Map<NamedType, ParameterizedType> typeMap) {
        List<TypeInfo> types = extractTypeInfo(typeOfObject, typeMap);
        // there's only one situation where we can have multiple types; that's multiple type bounds; only the first one can be a class
        TypeInfo typeInfo = types.get(0);
        if (parametersPresented == 0 && typeInfo.typeInspection.get().constructors.isEmpty()) {
            return List.of(new MethodCandidate(new MethodTypeParameterMap(emptyConstructor(typeInfo), Map.of()), Set.of()));
        }
        return typeInfo.typeInspection.get().constructors.stream()
                .filter(m -> parametersPresented == IGNORE_PARAMETER_NUMBERS || compatibleNumberOfParameters(m, parametersPresented))
                .map(m -> new MethodCandidate(new MethodTypeParameterMap(m, typeMap), findIndicesOfFunctionalInterfaces(m)))
                .collect(Collectors.toList());
    }

    public void recursivelyResolveOverloadedMethods(ParameterizedType typeOfObject,
                                                    String methodName,
                                                    int parametersPresented,
                                                    boolean decrementWhenNotStatic,
                                                    Map<NamedType, ParameterizedType> typeMap,
                                                    List<MethodCandidate> result) {
        recursivelyResolveOverloadedMethods(typeOfObject, methodName, parametersPresented, decrementWhenNotStatic, typeMap, result, new HashSet<>(), false);
    }

    private void recursivelyResolveOverloadedMethods(ParameterizedType typeOfObject,
                                                     String methodName,
                                                     int parametersPresented,
                                                     boolean decrementWhenNotStatic,
                                                     Map<NamedType, ParameterizedType> typeMap,
                                                     List<MethodCandidate> result,
                                                     Set<TypeInfo> visited,
                                                     boolean staticOnly) {
        List<TypeInfo> multipleTypeInfoObjects = extractTypeInfo(typeOfObject, typeMap);
        // more than one: only in the rare situation of multiple type bounds
        for (TypeInfo typeInfo : multipleTypeInfoObjects) {
            if (!visited.contains(typeInfo)) {
                visited.add(typeInfo);
                resolveOverloadedMethodsSingleType(typeInfo, staticOnly, methodName, parametersPresented, decrementWhenNotStatic, typeMap, result, visited);
            }
        }
        // it is possible that we find the method in one of the statically imported types... with * import
        for (TypeInfo typeInfo : importStaticAsterisk) {
            if (!visited.contains(typeInfo)) {
                visited.add(typeInfo);
                resolveOverloadedMethodsSingleType(typeInfo, true, methodName, parametersPresented, decrementWhenNotStatic, typeMap, result, visited);
            }
        }
        // or import by name
        TypeInfo byName = importStaticMemberToTypeInfo.get(methodName);
        if (byName != null && !visited.contains(byName)) {
            visited.add(byName);
            resolveOverloadedMethodsSingleType(byName, true, methodName, parametersPresented, decrementWhenNotStatic, typeMap, result, visited);
        }
    }

    private void resolveOverloadedMethodsSingleType(TypeInfo typeInfo,
                                                    boolean staticOnly,
                                                    String methodName,
                                                    int parametersPresented,
                                                    boolean decrementWhenNotStatic,
                                                    Map<NamedType, ParameterizedType> typeMap,
                                                    List<MethodCandidate> result,
                                                    Set<TypeInfo> visited) {
        if (!typeInfo.typeInspection.isSet()) {
            throw new UnsupportedOperationException("Type " + typeInfo.fullyQualifiedName + " has not been inspected yet");
        }
        typeInfo.typeInspection.get().methods.stream()
                .filter(m -> m.name.equals(methodName))
                .peek(m -> log(METHOD_CALL, "Considering {}", m.distinguishingName()))
                .filter(m -> !staticOnly || m.isStatic)
                .filter(m -> parametersPresented == IGNORE_PARAMETER_NUMBERS || compatibleNumberOfParameters(m, parametersPresented +
                        (!m.isStatic && decrementWhenNotStatic ? -1 : 0)))
                .map(m -> new MethodCandidate(new MethodTypeParameterMap(m, typeMap), findIndicesOfFunctionalInterfaces(m)))
                .forEach(result::add);

        ParameterizedType parentClass = typeInfo.typeInspection.get().parentClass;
        if (parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT) {
            recursivelyResolveOverloadedMethods(parentClass, methodName, parametersPresented, decrementWhenNotStatic, joinMaps(typeMap, parentClass), result, visited, staticOnly);
        }
        for (ParameterizedType interfaceImplemented : typeInfo.typeInspection.get().interfacesImplemented) {
            recursivelyResolveOverloadedMethods(interfaceImplemented, methodName, parametersPresented, decrementWhenNotStatic, joinMaps(typeMap, interfaceImplemented), result, visited, staticOnly);
        }
        if (!staticOnly && typeInfo != Primitives.PRIMITIVES.objectTypeInfo) {
            recursivelyResolveOverloadedMethods(Primitives.PRIMITIVES.objectParameterizedType, methodName, parametersPresented, decrementWhenNotStatic, typeMap, result, visited, staticOnly);
        }
    }

    private static Set<Integer> findIndicesOfFunctionalInterfaces(MethodInfo m) {
        Set<Integer> res = new HashSet<>();
        int i = 0;
        for (ParameterInfo parameterInfo : m.methodInspection.get().parameters) {
            if (parameterInfo.parameterizedType.typeInfo != null && parameterInfo.parameterizedType.typeInfo.typeInspection.isSet()) {
                TypeInspection typeInspection = parameterInfo.parameterizedType.typeInfo.typeInspection.get();
                if (typeInspection.typeNature == TypeNature.INTERFACE && typeInspection.annotations.contains(
                        Primitives.PRIMITIVES.functionalInterfaceAnnotationExpression)) {
                    res.add(i);
                }
            }
            i++;
        }
        return res;
    }

    private static boolean compatibleNumberOfParameters(MethodInfo m, int parametersPresented) {
        int declared = m.methodInspection.get().parameters.size();
        if (declared == 0) return parametersPresented == 0;
        boolean lastIsVarArgs = m.methodInspection.get().parameters.get(declared - 1).parameterInspection.get().varArgs;
        if (lastIsVarArgs) return parametersPresented >= declared - 1;
        return parametersPresented == declared;
    }

    private static Map<NamedType, ParameterizedType> joinMaps(Map<NamedType, ParameterizedType> previous,
                                                              ParameterizedType target) {
        HashMap<NamedType, ParameterizedType> res = new HashMap<>(previous);
        res.putAll(target.initialTypeParameterMap());
        return res;
    }

    public void addMessage(Message.Severity severity, String text) {
        Message message = new Message(severity, text);
        TypeContext parent = this;
        while (parent.parentContext != null) parent = parent.parentContext;
        parent.messages.add(message);
        LOGGER.info("Add message of severity {}: {}", severity, text);
    }

    public List<Message> getMessages() {
        return ImmutableList.copyOf(messages);
    }

    public boolean isPackagePrefix(PackagePrefix packagePrefix) {
        return typeStore.isPackagePrefix(packagePrefix);
    }

}
