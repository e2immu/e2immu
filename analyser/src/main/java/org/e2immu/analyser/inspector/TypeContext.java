/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.inspector;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.log;

/**
 * Inside a compilation unit, there is a context in which names are known.
 * This context is inherently recursive, dependent on the container.
 * <p>
 * All type contexts share the same type map.
 */
public class TypeContext implements InspectionProvider {
    private final TypeContext parentContext;

    public final TypeMapImpl.Builder typeMapBuilder;
    public final String packageName; // this one is filled in UNLESS parentContext == null, because that is the root level
    private final List<TypeInfo> importStaticAsterisk;
    private final Map<String, TypeInfo> importStaticMemberToTypeInfo;
    private final Map<String, NamedType> map = new HashMap<>();


    public TypeContext(TypeMapImpl.Builder typeMapBuilder) {
        this.typeMapBuilder = typeMapBuilder;
        parentContext = null;
        packageName = null;
        importStaticAsterisk = new ArrayList<>();
        importStaticMemberToTypeInfo = new HashMap<>();
    }

    public TypeContext(TypeContext parentContext) {
        this(parentContext.packageName, parentContext);
    }

    public TypeContext(String packageName, @NotNull TypeContext parentContext) {
        this.parentContext = Objects.requireNonNull(parentContext);
        typeMapBuilder = parentContext.typeMapBuilder;
        importStaticMemberToTypeInfo = new HashMap<>(parentContext.importStaticMemberToTypeInfo);
        importStaticAsterisk = new ArrayList<>(parentContext.importStaticAsterisk);
        this.packageName = packageName;
    }

    /*
    must be called AFTER the typeMapBuilder has the ByteCodeInspector set.
     */
    public void loadPrimitives() {
        for (TypeInfo typeInfo : getPrimitives().typeByName.values()) {
            addToContext(typeInfo);
        }
        for (TypeInfo typeInfo : getPrimitives().primitiveByName.values()) {
            addToContext(typeInfo);
        }
        typeMapBuilder.getE2ImmuAnnotationExpressions().streamTypes().forEach(this::addToContext);
    }

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
                namedType = typeMapBuilder.get(packageName + "." + name);
            }
        }
        if (namedType == null && parentContext == null) {
            if (name.contains(".")) {
                // we were not expecting an FQN here, but still we come across one
                return getFullyQualified(name, true);
            }
            namedType = typeMapBuilder.get("java.lang." + name);
        }
        if (namedType == null && complain) {
            throw new UnsupportedOperationException("Unknown type in context: " + name);
        }
        return namedType;
    }

    public TypeInfo getFullyQualified(Class<?> clazz) {
        return getFullyQualified(clazz.getCanonicalName(), true);
    }

    public TypeInfo getFullyQualified(String fullyQualifiedName, boolean complain) {
        TypeInfo typeInfo = typeMapBuilder.get(fullyQualifiedName);
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

    public void addToContext(String altName, @NotNull NamedType namedType, boolean allowOverwrite) {
        if (allowOverwrite || !map.containsKey(altName)) {
            map.put(altName, namedType);
        }
    }

    private MethodInspection createEmptyConstructor(@NotNull TypeInfo typeInfo) {
        MethodInspectionImpl.Builder constructorBuilder = new MethodInspectionImpl.Builder(typeInfo);
        return constructorBuilder.addModifier(MethodModifier.PUBLIC).build(this);
    }

    private List<TypeInfo> extractTypeInfo(ParameterizedType typeOfObject, Map<NamedType, ParameterizedType> typeMap) {
        TypeInfo typeInfo;
        if (typeOfObject.typeInfo == null) {
            if (typeOfObject.typeParameter == null) throw new UnsupportedOperationException();
            ParameterizedType pt = typeMap.get(typeOfObject.typeParameter);
            if (pt == null) {
                // rather than give an exception here, we replace t by the type that it extends, so that we can find those methods
                // in the case that there is no explicit extension/super, we replace it by the implicit Object
                List<ParameterizedType> typeBounds = typeOfObject.typeParameter.getTypeBounds();
                if (!typeBounds.isEmpty()) {
                    return typeBounds.stream().flatMap(bound -> extractTypeInfo(bound, typeMap).stream()).collect(Collectors.toList());
                } else {
                    typeInfo = getPrimitives().objectTypeInfo;
                }
            } else {
                typeInfo = pt.typeInfo;
            }
        } else {
            typeInfo = typeOfObject.typeInfo;
        }
        assert typeInfo != null;
        TypeInspection typeInspection = typeMapBuilder.getTypeInspection(typeInfo);
        if (typeInspection == null) {
            throw new UnsupportedOperationException("Type " + typeInfo.fullyQualifiedName + " has not been inspected");
        }
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
            TypeInspection typeInspection = getTypeInspection(typeInfo);
            typeInspection.fields().stream()
                    .filter(FieldInfo::isStatic)
                    .filter(f -> f.name.equals(memberName))
                    .findFirst()
                    .ifPresent(fieldInfo -> map.put(memberName, new FieldReference(this, fieldInfo, null)));
        }
        for (TypeInfo typeInfo : importStaticAsterisk) {
            TypeInspection typeInspection = getTypeInspection(typeInfo);
            typeInspection.fields().stream()
                    .filter(FieldInfo::isStatic)
                    .forEach(fieldInfo -> map.put(fieldInfo.name, new FieldReference(this, fieldInfo, null)));
        }
        return map;
    }

    @Override
    public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
        return typeMapBuilder.getFieldInspection(fieldInfo);
    }

    @Override
    public TypeInspection getTypeInspection(TypeInfo typeInfo) {
        return typeMapBuilder.getTypeInspection(typeInfo);
    }

    @Override
    public MethodInspection getMethodInspection(MethodInfo methodInfo) {
        return typeMapBuilder.getMethodInspection(methodInfo);
    }

    public Primitives getPrimitives() {
        return typeMapBuilder.getPrimitives();
    }

    public record MethodCandidate(MethodTypeParameterMap method, Set<Integer> parameterIndicesOfFunctionalInterfaces) {
    }

    public static final int IGNORE_PARAMETER_NUMBERS = -1;

    public List<MethodCandidate> resolveConstructor(ParameterizedType typeOfObject,
                                                    int parametersPresented,
                                                    Map<NamedType, ParameterizedType> typeMap) {
        List<TypeInfo> types = extractTypeInfo(typeOfObject, typeMap);
        // there's only one situation where we can have multiple types; that's multiple type bounds; only the first one can be a class
        TypeInfo typeInfo = types.get(0);
        TypeInspection typeInspection = getTypeInspection(typeInfo);
        if (parametersPresented == 0) {
            if (typeInspection.constructors().isEmpty()) {
                return List.of(new MethodCandidate(new MethodTypeParameterMap(createEmptyConstructor(typeInfo), Map.of()), Set.of()));
            }
        }
        return typeInspection.constructors().stream()
                .map(this::getMethodInspection)
                .filter(methodInspection -> parametersPresented == IGNORE_PARAMETER_NUMBERS ||
                        compatibleNumberOfParameters(methodInspection, parametersPresented))
                .map(methodInspection -> new MethodCandidate(new MethodTypeParameterMap(methodInspection, typeMap),
                        findIndicesOfFunctionalInterfaces(methodInspection)))
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
        TypeInspection typeInspection = Objects.requireNonNull(getTypeInspection(typeInfo));
        typeInspection.methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(m -> m.name.equals(methodName))
                .map(this::getMethodInspection)
                .peek(m -> log(METHOD_CALL, "Considering {}", m.getDistinguishingName()))
                .filter(m -> !staticOnly || m.isStatic())
                .filter(m -> parametersPresented == IGNORE_PARAMETER_NUMBERS ||
                        compatibleNumberOfParameters(m, parametersPresented +
                                (!m.isStatic() && decrementWhenNotStatic ? -1 : 0)))
                .map(m -> new MethodCandidate(new MethodTypeParameterMap(m, typeMap),
                        findIndicesOfFunctionalInterfaces(m)))
                .forEach(result::add);

        ParameterizedType parentClass = typeInspection.parentClass();
        boolean isJLO = Primitives.isJavaLangObject(typeInfo);
        assert isJLO || parentClass != null :
                "Parent class of " + typeInfo.fullyQualifiedName + " is null";
        if (!isJLO) {
            recursivelyResolveOverloadedMethods(parentClass, methodName, parametersPresented, decrementWhenNotStatic,
                    joinMaps(typeMap, parentClass), result, visited, staticOnly);
        }
        for (ParameterizedType interfaceImplemented : typeInspection.interfacesImplemented()) {
            recursivelyResolveOverloadedMethods(interfaceImplemented, methodName, parametersPresented,
                    decrementWhenNotStatic, joinMaps(typeMap, interfaceImplemented), result, visited, staticOnly);
        }
        if (typeInfo.packageNameOrEnclosingType.isRight()) {
            // if I'm in a static subtype, I can only access the static methods of the enclosing type
            ParameterizedType enclosingType = typeInfo.packageNameOrEnclosingType.getRight().asParameterizedType(this);
            boolean onlyStatic = staticOnly || typeInspection.isStatic();
            recursivelyResolveOverloadedMethods(enclosingType, methodName, parametersPresented, decrementWhenNotStatic,
                    joinMaps(typeMap, enclosingType), result, visited, onlyStatic);
        }
    }

    private Set<Integer> findIndicesOfFunctionalInterfaces(MethodInspection m) {
        Set<Integer> res = new HashSet<>();
        int i = 0;
        for (ParameterInfo parameterInfo : m.getParameters()) {
            if (parameterInfo.parameterizedType.typeInfo != null) {
                TypeInspection typeInspection = Objects.requireNonNull(getTypeInspection(parameterInfo.parameterizedType.typeInfo));
                if (typeInspection.typeNature() == TypeNature.INTERFACE && typeInspection.hasAnnotation(
                        getPrimitives().functionalInterfaceAnnotationExpression)) {
                    res.add(i);
                }
            }
            i++;
        }
        return res;
    }

    private boolean compatibleNumberOfParameters(MethodInspection m, int parametersPresented) {
        int declared = m.getParameters().size();
        if (declared == 0) return parametersPresented == 0;
        boolean lastIsVarArgs = m.getParameters().get(declared - 1).parameterInspection.get().isVarArgs();
        if (lastIsVarArgs) return parametersPresented >= declared - 1;
        return parametersPresented == declared;
    }

    private Map<NamedType, ParameterizedType> joinMaps(Map<NamedType, ParameterizedType> previous,
                                                       ParameterizedType target) {
        HashMap<NamedType, ParameterizedType> res = new HashMap<>(previous);
        res.putAll(target.initialTypeParameterMap(this));
        return res;
    }

    public boolean isPackagePrefix(PackagePrefix packagePrefix) {
        return typeMapBuilder.isPackagePrefix(packagePrefix);
    }

}
