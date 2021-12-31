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

package org.e2immu.analyser.inspector;

import org.e2immu.analyser.inspector.expr.Scope;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.e2immu.analyser.parser.TypeAndInspectionProvider;
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
public class TypeContext implements TypeAndInspectionProvider {
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
        for (TypeInfo typeInfo : getPrimitives().getTypeByName().values()) {
            addToContext(typeInfo);
        }
        for (TypeInfo typeInfo : getPrimitives().getPrimitiveByName().values()) {
            addToContext(typeInfo);
        }
        typeMapBuilder.getE2ImmuAnnotationExpressions().streamTypes().forEach(this::addToContext);
    }

    /**
     * used for: Annotation types, ParameterizedTypes (general types)
     *
     * @param name     the name to search for; no idea if it is a simple name, a semi qualified, or a fully qualified
     *                 name
     * @param complain throw an error when the name is unknown
     * @return the NamedType with that name
     */
    public NamedType get(@NotNull String name, boolean complain) {
        NamedType simple = getSimpleName(name);
        if (simple != null) return simple;

        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            // name can be fully qualified, or semi qualified
            // try fully qualified first
            NamedType fullyQualified = getFullyQualified(name, false);
            if (fullyQualified != null) return fullyQualified;
            // it must be semi qualified now... go recursive
            String prefix = name.substring(0, dot);
            NamedType prefixType = get(prefix, complain);
            if (prefixType instanceof TypeInfo typeInfo) {
                String fqn = typeInfo.fullyQualifiedName + "." + name.substring(dot + 1);
                return getFullyQualified(fqn, complain);
            }
            throw new UnsupportedOperationException("?");
        }
        // try out java.lang; has been pre-loaded
        TypeInfo inJavaLang = typeMapBuilder.get("java.lang." + name);
        if (inJavaLang != null) return inJavaLang;

        // go fully qualified using the package
        String fqn = packageName + "." + name;
        return getFullyQualified(fqn, complain);
    }

    private NamedType getSimpleName(String name) {
        NamedType namedType = map.get(name);
        if (namedType != null) return namedType;
        if (parentContext != null) {
            return parentContext.getSimpleName(name);
        }
        return null;
    }

    @Override
    public TypeInfo getFullyQualified(Class<?> clazz) {
        return getFullyQualified(clazz.getCanonicalName(), true);
    }

    /**
     * Look up a type by FQN. Actual loading using loadType takes place when a type is mentioned by FQN, bypassing
     * the more common import system.
     *
     * @param fullyQualifiedName the fully qualified name, such as java.lang.String
     * @return the type
     */
    public TypeInfo getFullyQualified(String fullyQualifiedName, boolean complain) {
        TypeInfo typeInfo = typeMapBuilder.get(fullyQualifiedName);
        if (typeInfo == null) {
            return typeMapBuilder.loadType(fullyQualifiedName, complain);
        }
        return typeInfo;
    }

    public boolean isKnown(String fullyQualified) {
        return typeMapBuilder.get(fullyQualified) != null;
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
                    typeInfo = getPrimitives().objectTypeInfo();
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
                    .filter(fieldInfo -> getFieldInspection(fieldInfo).isStatic())
                    .filter(f -> f.name.equals(memberName))
                    .findFirst()
                    .ifPresent(fieldInfo -> map.put(memberName, new FieldReference(this, fieldInfo, null)));
        }
        for (TypeInfo typeInfo : importStaticAsterisk) {
            TypeInspection typeInspection = getTypeInspection(typeInfo);
            typeInspection.fields().stream()
                    .filter(fieldInfo -> getFieldInspection(fieldInfo).getModifiers().contains(FieldModifier.STATIC))
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


    public record MethodCandidate(MethodTypeParameterMap method, int distance) {
        public MethodCandidate(MethodTypeParameterMap method) {
            this(method, 0);
        }
    }

    public List<MethodCandidate> resolveConstructorInvocation(TypeInfo startingPoint,
                                                              int parametersPresented) {
        ParameterizedType type = startingPoint.asParameterizedType(this);
        return resolveConstructor(type, type, parametersPresented, Map.of());
    }

    public static final int IGNORE_PARAMETER_NUMBERS = -1;

    public List<MethodCandidate> resolveConstructor(ParameterizedType formalType,
                                                    ParameterizedType concreteType,
                                                    int parametersPresented,
                                                    Map<NamedType, ParameterizedType> typeMap) {
        List<TypeInfo> types = extractTypeInfo(concreteType != null ? concreteType : formalType, typeMap);
        // there's only one situation where we can have multiple types; that's multiple type bounds; only the first one can be a class
        TypeInfo typeInfo = types.get(0);
        TypeInspection typeInspection = getTypeInspection(typeInfo);

        return typeInspection.constructors().stream()
                .map(this::getMethodInspection)
                .filter(methodInspection -> parametersPresented == IGNORE_PARAMETER_NUMBERS ||
                        compatibleNumberOfParameters(methodInspection, parametersPresented))
                .map(methodInspection -> new MethodCandidate(new MethodTypeParameterMap(methodInspection, typeMap)))
                .collect(Collectors.toList());
    }

    public void recursivelyResolveOverloadedMethods(ParameterizedType typeOfObject,
                                                    String methodName,
                                                    int parametersPresented,
                                                    boolean decrementWhenNotStatic,
                                                    Map<NamedType, ParameterizedType> typeMap,
                                                    List<MethodCandidate> result,
                                                    Scope.ScopeNature scopeNature) {
        recursivelyResolveOverloadedMethods(typeOfObject, methodName, parametersPresented, decrementWhenNotStatic,
                typeMap, result, new HashSet<>(), false, scopeNature, 0);
    }

    private void recursivelyResolveOverloadedMethods(ParameterizedType typeOfObject,
                                                     String methodName,
                                                     int parametersPresented,
                                                     boolean decrementWhenNotStatic,
                                                     Map<NamedType, ParameterizedType> typeMap,
                                                     List<MethodCandidate> result,
                                                     Set<TypeInfo> visited,
                                                     boolean staticOnly,
                                                     Scope.ScopeNature scopeNature,
                                                     int distance) {
        List<TypeInfo> multipleTypeInfoObjects = extractTypeInfo(typeOfObject, typeMap);
        // more than one: only in the rare situation of multiple type bounds
        for (TypeInfo typeInfo : multipleTypeInfoObjects) {
            if (!visited.contains(typeInfo)) {
                visited.add(typeInfo);
                resolveOverloadedMethodsSingleType(typeInfo, staticOnly, scopeNature, methodName, parametersPresented,
                        decrementWhenNotStatic, typeMap, result, visited, distance + 2);
            }
        }
        // it is possible that we find the method in one of the statically imported types... with * import
        for (TypeInfo typeInfo : importStaticAsterisk) {
            if (!visited.contains(typeInfo)) {
                visited.add(typeInfo);
                resolveOverloadedMethodsSingleType(typeInfo, true, scopeNature, methodName,
                        parametersPresented, decrementWhenNotStatic, typeMap, result, visited, distance + 1);
            }
        }
        // or import by name
        TypeInfo byName = importStaticMemberToTypeInfo.get(methodName);
        if (byName != null && !visited.contains(byName)) {
            visited.add(byName);
            resolveOverloadedMethodsSingleType(byName, true, scopeNature, methodName,
                    parametersPresented, decrementWhenNotStatic, typeMap, result, visited, distance);
        }
    }

    private void resolveOverloadedMethodsSingleType(TypeInfo typeInfo,
                                                    boolean staticOnly,
                                                    Scope.ScopeNature scopeNature,
                                                    String methodName,
                                                    int parametersPresented,
                                                    boolean decrementWhenNotStatic,
                                                    Map<NamedType, ParameterizedType> typeMap,
                                                    List<MethodCandidate> result,
                                                    Set<TypeInfo> visited,
                                                    int distance) {
        TypeInspection typeInspection = Objects.requireNonNull(getTypeInspection(typeInfo));
        typeInspection.methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(m -> m.name.equals(methodName))
                .map(this::getMethodInspection)
                .peek(m -> log(METHOD_CALL, "Considering {}", m.getDistinguishingName()))
                .filter(m -> !staticOnly || m.isStatic())
                .filter(m -> parametersPresented == IGNORE_PARAMETER_NUMBERS ||
                        compatibleNumberOfParameters(m, parametersPresented +
                                (!m.isStatic() && decrementWhenNotStatic ? -1 : 0)))
                .map(m -> new MethodCandidate(new MethodTypeParameterMap(m, typeMap), distance))
                .forEach(result::add);

        ParameterizedType parentClass = typeInspection.parentClass();
        boolean isJLO = Primitives.isJavaLangObject(typeInfo);
        assert isJLO || parentClass != null :
                "Parent class of " + typeInfo.fullyQualifiedName + " is null";
        int numInterfaces = typeInspection.interfacesImplemented().size();
        if (!isJLO) {
            recursivelyResolveOverloadedMethods(parentClass, methodName, parametersPresented, decrementWhenNotStatic,
                    joinMaps(typeMap, parentClass), result, visited, staticOnly, scopeNature, distance + numInterfaces + 1);
        }
        int count = 0;
        for (ParameterizedType interfaceImplemented : typeInspection.interfacesImplemented()) {
            recursivelyResolveOverloadedMethods(interfaceImplemented, methodName, parametersPresented,
                    decrementWhenNotStatic, joinMaps(typeMap, interfaceImplemented), result, visited, staticOnly,
                    scopeNature, distance + count);
            ++count;
        }
        // See UtilityClass_2 for an example where we should go to the static methods of the enclosing type
        if (typeInfo.packageNameOrEnclosingType.isRight()) {
            // if I'm in a static subtype, I can only access the static methods of the enclosing type
            boolean onlyStatic = staticOnly || typeInspection.isStatic();
            if (onlyStatic && scopeNature != Scope.ScopeNature.INSTANCE ||
                    !onlyStatic && scopeNature != Scope.ScopeNature.STATIC) {
                ParameterizedType enclosingType = typeInfo.packageNameOrEnclosingType.getRight().asParameterizedType(this);
                recursivelyResolveOverloadedMethods(enclosingType, methodName, parametersPresented, decrementWhenNotStatic,
                        joinMaps(typeMap, enclosingType), result, visited, onlyStatic, scopeNature, distance + numInterfaces);
            }
        }
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
