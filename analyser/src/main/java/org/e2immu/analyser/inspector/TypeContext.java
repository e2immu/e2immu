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
public interface TypeContext extends TypeAndInspectionProvider {

    default TypeMap.Builder typeMap() {
        throw new UnsupportedOperationException();
    }

    /**
     * used for: Annotation types, ParameterizedTypes (general types)
     *
     * @param name     the name to search for; no idea if it is a simple name, a semi qualified, or a fully qualified
     *                 name
     * @param complain throw an error when the name is unknown
     * @return the NamedType with that name
     */
    default NamedType get(@NotNull String name, boolean complain) {
        throw new UnsupportedOperationException();
    }

    @Override
    default TypeInfo getFullyQualified(Class<?> clazz) {
        return getFullyQualified(clazz.getCanonicalName(), true);
    }

    default NamedType getSimpleName(String name) {
        throw new UnsupportedOperationException();
    }

    /**
     * Look up a type by FQN. Ensure that the type has been inspected.
     *
     * @param fullyQualifiedName the fully qualified name, such as java.lang.String
     * @return the type
     */
    default TypeInfo getFullyQualified(String fullyQualifiedName, boolean complain) {
        throw new UnsupportedOperationException();
    }

    default boolean isKnown(String fullyQualified) {
        return typeMap().get(fullyQualified) != null;
    }

    default void addToContext(@NotNull NamedType namedType) {
        addToContext(namedType, true);
    }

    default void addToContext(@NotNull NamedType namedType, boolean allowOverwrite) {
        throw new UnsupportedOperationException();
    }

    default void addToContext(String altName, @NotNull NamedType namedType, boolean allowOverwrite) {
        throw new UnsupportedOperationException();
    }

    default Map<String, FieldReference> staticFieldImports() {
        throw new UnsupportedOperationException();
    }

    @Override
    default FieldInspection getFieldInspection(FieldInfo fieldInfo) {
        return typeMap().getFieldInspection(fieldInfo);
    }

    @Override
    default TypeInspection getTypeInspection(TypeInfo typeInfo) {
        return typeMap().getTypeInspection(typeInfo);
    }

    @Override
    default MethodInspection getMethodInspection(MethodInfo methodInfo) {
        return typeMap().getMethodInspection(methodInfo);
    }

    default Primitives getPrimitives() {
        return typeMap().getPrimitives();
    }


    default Map<MethodTypeParameterMap, Integer> resolveConstructorInvocation(TypeInfo startingPoint,
                                                                              int parametersPresented) {
        throw new UnsupportedOperationException();
    }

    default Map<MethodTypeParameterMap, Integer> resolveConstructor(ParameterizedType formalType,
                                                                    ParameterizedType concreteType,
                                                                    int parametersPresented,
                                                                    Map<NamedType, ParameterizedType> typeMap) {
        throw new UnsupportedOperationException();
    }

    default void recursivelyResolveOverloadedMethods(ParameterizedType typeOfObject,
                                                     String methodName,
                                                     int parametersPresented,
                                                     boolean decrementWhenNotStatic,
                                                     Map<NamedType, ParameterizedType> typeMap,
                                                     Map<MethodTypeParameterMap, Integer> result,
                                                     Scope.ScopeNature scopeNature) {
        throw new UnsupportedOperationException();
    }

    default boolean isPackagePrefix(PackagePrefix packagePrefix) {
        return typeMap().isPackagePrefix(packagePrefix);
    }

    @Override
    default MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier, TypeInfo typeInfo, String methodName) {
        return typeMap().newMethodInspectionBuilder(identifier, typeInfo, methodName);
    }

    default String packageName() {
        throw new UnsupportedOperationException();
    }

    default ImportMap importMap() {
        throw new UnsupportedOperationException();
    }
}
