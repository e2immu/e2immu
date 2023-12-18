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
import org.e2immu.analyser.model.variable.impl.FieldReferenceImpl;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeAndInspectionProvider;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Inside a compilation unit, there is a context in which names are known.
 * This context is inherently recursive, dependent on the container.
 * <p>
 * All type contexts share the same type map.
 */
public class TypeContext implements TypeAndInspectionProvider {
    private final TypeContext parentContext;

    public final TypeMap.Builder typeMap;
    public final String packageName; // this one is filled in UNLESS parentContext == null, because that is the root level
    private final ImportMap importMap;
    private final Map<String, NamedType> map = new HashMap<>();

    public TypeContext(TypeMap.Builder typeMap) {
        this.typeMap = typeMap;
        parentContext = null;
        packageName = null;
        importMap = new ImportMap();
    }

    public TypeContext(TypeContext parentContext) {
        this(parentContext.packageName, parentContext, true);
    }

    public TypeContext(String packageName, @NotNull TypeContext parentContext, boolean copyImportMap) {
        this.parentContext = Objects.requireNonNull(parentContext);
        this.packageName = packageName;
        typeMap = parentContext.typeMap;
        importMap = copyImportMap ? parentContext.importMap : new ImportMap();
    }

    public TypeMap.Builder typeMap() {
        return typeMap;
    }

    @Override
    public MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier, TypeInfo typeInfo, String methodName) {
        return typeMap.newMethodInspectionBuilder(identifier, typeInfo, methodName);
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
        typeMap.getE2ImmuAnnotationExpressions().streamTypes().forEach(this::addToContext);
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
        // try out java.lang; has been preloaded
        TypeInfo inJavaLang = typeMap.get("java.lang." + name);
        if (inJavaLang != null) return inJavaLang;

        // go fully qualified using the package
        String fqn = packageName + "." + name;
        return getFullyQualified(fqn, complain);
    }

    private NamedType getSimpleName(String name) {
        NamedType namedType = map.get(name);
        if (namedType != null) {
            return namedType;
        }

        // explicit imports
        TypeInfo fromImport = importMap.getSimpleName(name);
        if (fromImport != null) {
            return fromImport;
        }

        // Same package, and * imports (in that order!)
        if (parentContext != null) {
            NamedType fromParent = parentContext.getSimpleName(name);
            if (fromParent != null) {
                return fromParent;
            }
        }

        /*
        On-demand: subtype from import statement (see e.g. Import_2)
        This is done on-demand to fight cyclic dependencies if we do eager inspection.
         */
        TypeInfo parent = importMap.getStaticMemberToTypeInfo(name);
        if (parent != null) {
            TypeInspection parentInspection = typeMap.getTypeInspection(parent);
            TypeInfo subType = parentInspection.subTypes()
                    .stream().filter(st -> name.equals(st.simpleName)).findFirst().orElse(null);
            if (subType != null) {
                importMap.putTypeMap(subType.fullyQualifiedName, subType, false, false);
                return subType;
            }
        }
        /*
        On-demand: try to resolve the * imports registered in this type context
         */
        for (TypeInfo wildcard : importMap.importAsterisk()) {
            // the call to getTypeInspection triggers the JavaParser
            TypeInspection typeInspection = typeMap.getTypeInspection(wildcard);
            if (typeInspection != null) {
                TypeInfo subType = typeInspection.subTypes()
                        .stream().filter(st -> name.equals(st.simpleName)).findFirst().orElse(null);
                if (subType != null) {
                    importMap.putTypeMap(subType.fullyQualifiedName, subType, false, false);
                    return subType;
                }
            }
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
        TypeInfo typeInfo = typeMap.get(fullyQualifiedName);
        if (typeInfo == null) {
            // see InspectionGaps_9: we don't have the type, but we do have an import of its enclosing type
            TypeInfo imported = importMap.isImported(fullyQualifiedName);
            if (imported != null) {
                return imported;
            }
            return typeMap.getOrCreate(fullyQualifiedName, complain);
        }
        return typeInfo;
    }

    public boolean isKnown(String fullyQualified) {
        return typeMap.get(fullyQualified) != null;
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
            if (typeOfObject.typeParameter == null) {
                throw new UnsupportedOperationException();
            }
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
        TypeInspection typeInspection = this.typeMap.getTypeInspection(typeInfo);
        if (typeInspection == null) {
            throw new UnsupportedOperationException("Type " + typeInfo.fullyQualifiedName + " has not been inspected");
        }
        return List.of(typeInfo);
    }

    public void addImportStaticWildcard(TypeInfo typeInfo) {
        importMap.addStaticAsterisk(typeInfo);
    }

    public void addImportStatic(TypeInfo typeInfo, String member) {
        importMap.putStaticMemberToTypeInfo(member, typeInfo);
    }

    public Map<String, FieldReference> staticFieldImports() {
        Map<String, FieldReference> map = new HashMap<>();
        for (Map.Entry<String, TypeInfo> entry : importMap.staticMemberToTypeInfoEntrySet()) {
            TypeInfo typeInfo = entry.getValue();
            String memberName = entry.getKey();
            TypeInspection typeInspection = getTypeInspection(typeInfo);
            typeInspection.fields().stream()
                    .filter(fieldInfo -> getFieldInspection(fieldInfo).isStatic())
                    .filter(f -> f.name.equals(memberName))
                    .findFirst()
                    .ifPresent(fieldInfo -> map.put(memberName, new FieldReferenceImpl(this, fieldInfo)));
        }
        for (TypeInfo typeInfo : importMap.staticAsterisk()) {
            TypeInspection typeInspection = getTypeInspection(typeInfo);
            typeInspection.fields().stream()
                    .filter(fieldInfo -> getFieldInspection(fieldInfo).isStatic())
                    .forEach(fieldInfo -> map.put(fieldInfo.name, new FieldReferenceImpl(this, fieldInfo)));
        }
        return map;
    }

    @Override
    public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
        return typeMap.getFieldInspection(fieldInfo);
    }

    @Override
    public TypeInspection getTypeInspection(TypeInfo typeInfo) {
        return typeMap.getTypeInspection(typeInfo);
    }

    @Override
    public MethodInspection getMethodInspection(MethodInfo methodInfo) {
        return typeMap.getMethodInspection(methodInfo);
    }

    public Primitives getPrimitives() {
        return typeMap.getPrimitives();
    }

    public void addImport(TypeInfo typeInfo, boolean highPriority, boolean directImport) {
        importMap.putTypeMap(typeInfo.fullyQualifiedName, typeInfo, highPriority, directImport);
        if (!directImport) {
            addToContext(typeInfo, highPriority);
        }
    }

    public void addImportWildcard(TypeInfo typeInfo) {
        importMap.addToSubtypeAsterisk(typeInfo);
        // not adding the type to the context!!! the subtypes will be added by the inspector
    }

    public Map<MethodTypeParameterMap, Integer> resolveConstructorInvocation(TypeInfo startingPoint,
                                                                             int parametersPresented) {
        ParameterizedType type = startingPoint.asParameterizedType(this);
        return resolveConstructor(type, type, parametersPresented, Map.of());
    }

    public static final int IGNORE_PARAMETER_NUMBERS = -1;

    public Map<MethodTypeParameterMap, Integer> resolveConstructor(ParameterizedType formalType,
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
                .map(mi -> new MethodTypeParameterMap(mi, typeMap))
                .collect(Collectors.toMap(mt -> mt, mt -> 1));
    }

    public void recursivelyResolveOverloadedMethods(ParameterizedType typeOfObject,
                                                    String methodName,
                                                    int parametersPresented,
                                                    boolean decrementWhenNotStatic,
                                                    Map<NamedType, ParameterizedType> typeMap,
                                                    Map<MethodTypeParameterMap, Integer> result,
                                                    Scope.ScopeNature scopeNature) {
        recursivelyResolveOverloadedMethods(typeOfObject, methodName, parametersPresented, decrementWhenNotStatic,
                typeMap, result, new HashSet<>(), new HashSet<>(), false, scopeNature, 0);
    }

    private void recursivelyResolveOverloadedMethods(ParameterizedType typeOfObject,
                                                     String methodName,
                                                     int parametersPresented,
                                                     boolean decrementWhenNotStatic,
                                                     Map<NamedType, ParameterizedType> typeMap,
                                                     Map<MethodTypeParameterMap, Integer> result,
                                                     Set<TypeInfo> visited,
                                                     Set<TypeInfo> visitedStatic,
                                                     boolean staticOnly,
                                                     Scope.ScopeNature scopeNature,
                                                     int distance) {
        List<TypeInfo> multipleTypeInfoObjects = extractTypeInfo(typeOfObject, typeMap);
        // more than one: only in the rare situation of multiple type bounds
        for (TypeInfo typeInfo : multipleTypeInfoObjects) {
            Set<TypeInfo> types = staticOnly ? visitedStatic : visited;
            if (!types.contains(typeInfo)) {
                if (!staticOnly) visited.add(typeInfo);
                visitedStatic.add(typeInfo);
                resolveOverloadedMethodsSingleType(typeInfo, staticOnly, scopeNature, methodName, parametersPresented,
                        decrementWhenNotStatic, typeMap, result, visited, visitedStatic, distance + 2);
            }
        }
        // it is possible that we find the method in one of the statically imported types... with * import
        // if the method is static, we must be talking about the same type (See Import_10).
        for (TypeInfo typeInfo : importMap.staticAsterisk()) {
            if (!visited.contains(typeInfo) && !visitedStatic.contains(typeInfo)
                    && (scopeNature != Scope.ScopeNature.STATIC || typeInfo == typeOfObject.bestTypeInfo())) {
                visitedStatic.add(typeInfo);
                resolveOverloadedMethodsSingleType(typeInfo, true, scopeNature, methodName,
                        parametersPresented, decrementWhenNotStatic, typeMap, result, visited, visitedStatic,
                        distance + 1);
            }
        }
        // or import by name
        TypeInfo byName = importMap.getStaticMemberToTypeInfo(methodName);
        if (byName != null && !visited.contains(byName) && !visitedStatic.contains(byName)) {
            visitedStatic.add(byName);
            resolveOverloadedMethodsSingleType(byName, true, scopeNature, methodName,
                    parametersPresented, decrementWhenNotStatic, typeMap, result, visited, visitedStatic, distance);
        }
    }

    private void resolveOverloadedMethodsSingleType(TypeInfo typeInfo,
                                                    boolean staticOnly,
                                                    Scope.ScopeNature scopeNature,
                                                    String methodName,
                                                    int parametersPresented,
                                                    boolean decrementWhenNotStatic,
                                                    Map<NamedType, ParameterizedType> typeMap,
                                                    Map<MethodTypeParameterMap, Integer> result,
                                                    Set<TypeInfo> visited,
                                                    Set<TypeInfo> visitedStatic,
                                                    int distance) {
        TypeInspection typeInspection = Objects.requireNonNull(getTypeInspection(typeInfo));
        boolean shallowAnalysis = !typeInspection.inspector().statements();
        typeInspection.methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(m -> m.name.equals(methodName))
                .map(this::getMethodInspection)
                .filter(m -> !staticOnly || m.getMethodInfo().isStatic())
                .filter(m -> parametersPresented == IGNORE_PARAMETER_NUMBERS ||
                        compatibleNumberOfParameters(m, parametersPresented +
                                (!m.getMethodInfo().isStatic() && decrementWhenNotStatic ? -1 : 0)))
                .forEach(m -> {
                    MethodTypeParameterMap mt = new MethodTypeParameterMap(m, typeMap);
                    int score = distance
                            // add a penalty for shallowly analysed, non-public methods
                            // See the java.lang.StringBuilder AbstractStringBuilder CharSequence length() problem
                            + (shallowAnalysis && !m.isPubliclyAccessible(this) ? 100 : 0)
                            // see e.g. MethodCall_70, where we must choose between a static and instance method
                            + (m.getMethodInfo().isStatic() && scopeNature == Scope.ScopeNature.INSTANCE ? 100 : 0);
                    result.merge(mt, score, Integer::min);
                });


        ParameterizedType parentClass = typeInspection.parentClass();
        boolean isJLO = typeInfo.isJavaLangObject();
        assert isJLO || parentClass != null :
                "Parent class of " + typeInfo.fullyQualifiedName + " is null";
        int numInterfaces = typeInspection.interfacesImplemented().size();
        if (!isJLO) {
            recursivelyResolveOverloadedMethods(parentClass, methodName, parametersPresented, decrementWhenNotStatic,
                    joinMaps(typeMap, parentClass), result, visited, visitedStatic, staticOnly, scopeNature,
                    distance + numInterfaces + 1);
        }
        int count = 0;
        for (ParameterizedType interfaceImplemented : typeInspection.interfacesImplemented()) {
            recursivelyResolveOverloadedMethods(interfaceImplemented, methodName, parametersPresented,
                    decrementWhenNotStatic, joinMaps(typeMap, interfaceImplemented), result, visited, visitedStatic,
                    staticOnly, scopeNature, distance + count);
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
                        joinMaps(typeMap, enclosingType), result, visited, visitedStatic,
                        onlyStatic, scopeNature, distance + numInterfaces);
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
        return typeMap.isPackagePrefix(packagePrefix);
    }

    public void recursivelyAddVisibleSubTypes(TypeInfo typeInfo) {
        TypeInspection typeInspection = getTypeInspection(typeInfo);
        typeInspection.subTypes()
                .stream().filter(st -> !getTypeInspection(st).isPrivate())
                .forEach(this::addToContext);
        if (!typeInspection.parentClass().isJavaLangObject()) {
            recursivelyAddVisibleSubTypes(typeInspection.parentClass().typeInfo);
        }
        for (ParameterizedType interfaceImplemented : typeInspection.interfacesImplemented()) {
            recursivelyAddVisibleSubTypes(interfaceImplemented.typeInfo);
        }
    }
}
