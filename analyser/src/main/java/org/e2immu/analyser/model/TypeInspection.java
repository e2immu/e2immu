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

import org.e2immu.analyser.analyser.SetOfTypes;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * all the fields are deeply immutable or in the case of TypeInfo, eventually immutable.
 */
public interface TypeInspection extends Inspection {
    // the type that this inspection object belongs to
    @NotNull
    TypeInfo typeInfo();

    MethodInfo enclosingMethod();

    @NotNull
    TypeNature typeNature();

    ParameterizedType parentClass();

    //@Immutable(level = 2, after="TypeAnalyser.analyse()")
    @NotNull(content = true)
    List<MethodInfo> constructors();

    @NotNull(content = true)
    List<MethodInfo> methods();

    @NotNull(content = true)
    List<FieldInfo> fields();

    @NotNull(content = true)
    Set<TypeModifier> modifiers();

    @NotNull(content = true)
    List<TypeInfo> subTypes();

    @NotNull(content = true)
    List<TypeParameter> typeParameters();

    @NotNull(content = true)
    List<ParameterizedType> interfacesImplemented();

    @Nullable // when isFunctionalInterface is false
    MethodInspection getSingleAbstractMethod();

    @NotNull
    Inspector inspector();

    /**
     * Returns the types permitted to extend from this type.
     *
     * @return The types permitted to extend from this type. Note that this list is not empty
     * if and only if the type is sealed.
     */
    @NotNull(content = true)
    List<TypeInfo> permittedWhenSealed();

    boolean isFunctionalInterface();

    boolean isExtensible();

    default boolean isAbstract() {
        if (typeNature() == TypeNature.INTERFACE) return true;
        return modifiers().contains(TypeModifier.ABSTRACT);
    }

    enum Methods {

        THIS_TYPE_ONLY(false, false, null),
        THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM(false, false, null),
        THIS_TYPE_ONLY_EXCLUDE_FIELD_ARTIFICIAL_SAM(false, false, null),
        INCLUDE_SUBTYPES(true, false, THIS_TYPE_ONLY),
        INCLUDE_SUPERTYPES(false, true, THIS_TYPE_ONLY);

        Methods(boolean recurseIntoSubTypes, boolean recurseIntoSuperTypes, Methods nonRecursiveVariant) {
            this.recurseIntoSubTypes = recurseIntoSubTypes;
            this.recurseIntoSuperTypes = recurseIntoSuperTypes;
            this.nonRecursiveVariant = nonRecursiveVariant;
        }

        final boolean recurseIntoSubTypes;
        final boolean recurseIntoSuperTypes;
        final Methods nonRecursiveVariant;
    }

    default Stream<MethodInfo> methodsAndConstructors(Methods methodsMode) {
        return Stream.concat(constructorStream(methodsMode), methodStream(methodsMode));
    }

    default Stream<MethodInfo> methodStream(Methods methodsMode) {
        if (methodsMode.recurseIntoSubTypes) {
            return Stream.concat(nonRecursiveMethodStream(methodsMode.nonRecursiveVariant),
                    subTypes().stream()
                            .filter(subType -> subType.typeInspection.isSet())
                            .flatMap(subType -> subType.typeInspection.get().methodStream(methodsMode)));
        }
        if (methodsMode.recurseIntoSuperTypes) {
            return Stream.concat(nonRecursiveMethodStream(methodsMode.nonRecursiveVariant),
                    typeInfo().typeResolution.get().superTypesExcludingJavaLangObject().stream()
                            .flatMap(superType -> superType.typeInspection.get()
                                    .nonRecursiveMethodStream(methodsMode.nonRecursiveVariant)));
        }
        return nonRecursiveMethodStream(methodsMode);
    }

    private Stream<MethodInfo> nonRecursiveMethodStream(Methods methodsMode) {
        if (methodsMode == Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM) return methods().stream();
        return Stream.concat(methods().stream(),
                methodsInFieldInitializers(methodsMode != Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_ARTIFICIAL_SAM));
    }

    default Stream<MethodInfo> constructorStream(Methods methodsMode) {
        if (methodsMode.recurseIntoSubTypes) {
            return Stream.concat(constructors().stream(), subTypes().stream()
                    .filter(subType -> subType.typeInspection.isSet())
                    .flatMap(subType -> subType.typeInspection.get(subType.fullyQualifiedName).constructorStream(methodsMode)));
        }
        if (methodsMode.recurseIntoSuperTypes) {
            return Stream.concat(constructors().stream(),
                    typeInfo().typeResolution.get().superTypesExcludingJavaLangObject().stream()
                            .flatMap(superType -> superType.typeInspection.get().constructors().stream()));
        }
        return constructors().stream();
    }

    default Iterable<MethodInfo> methods(Methods methodsMode) {
        return () -> methodStream(methodsMode).iterator();
    }

    default List<MethodInfo> methodsAndConstructors() {
        return ListUtil.concatImmutable(methods(), constructors());
    }

    default Stream<MethodInfo> methodsInFieldInitializers(boolean alsoSynthetic) {
        return fields().stream()
                .filter(fieldInfo -> fieldInfo.fieldInspection.get().fieldInitialiserIsSet())
                .map(fieldInfo -> fieldInfo.fieldInspection.get().getFieldInitialiser())
                .filter(initialiser -> initialiser.implementationOfSingleAbstractMethod() != null
                        && (alsoSynthetic ||
                        !initialiser.implementationOfSingleAbstractMethod().methodInspection.get().isSynthetic()))
                .map(FieldInspection.FieldInitialiser::implementationOfSingleAbstractMethod);
    }

    /**
     * @param inspectionProvider to be able to inspect super-types
     * @return not null when a functional interface
     */
    MethodInspection computeIsFunctionalInterface(InspectionProvider inspectionProvider);

    default boolean isStatic() {
        if (typeInfo().packageNameOrEnclosingType.isLeft()) return true; // independent type
        return typeNature() != TypeNature.CLASS || modifiers().contains(TypeModifier.STATIC); // static subtype
    }

    default boolean isInterface() {
        return typeNature() == TypeNature.INTERFACE;
    }

    default boolean isEnum() {
        return typeNature() == TypeNature.ENUM;
    }

    default boolean isRecord() {
        return typeNature() == TypeNature.RECORD;
    }

    default boolean isAnnotation() {
        return typeNature() == TypeNature.ANNOTATION;
    }

    default boolean isSealed() {
        return !permittedWhenSealed().isEmpty();
    }

    @SuppressWarnings("unused")
    default Set<ParameterizedType> typesOfFieldsMethodsConstructors(InspectionProvider inspectionProvider) {
        // this type
        Set<ParameterizedType> typesOfFields = fields().stream()
                .map(fieldInfo -> fieldInfo.type).collect(Collectors.toCollection(HashSet::new));
        typesOfFields.addAll(typesOfMethodsAndConstructors(inspectionProvider).types());
        return typesOfFields;
    }

    default SetOfTypes typesOfMethodsAndConstructors(InspectionProvider inspectionProvider) {
        Set<ParameterizedType> result = new HashSet<>();
        for (MethodInfo methodInfo : methodsAndConstructors()) {
            if (!methodInfo.isConstructor() && !methodInfo.isVoid()) {
                result.add(methodInfo.returnType());
            }
            for (ParameterInfo parameterInfo : inspectionProvider.getMethodInspection(methodInfo).getParameters()) {
                result.add(parameterInfo.parameterizedType);
            }
        }
        return new SetOfTypes(result);
    }

    static String createStaticBlockMethodName(int identifier) {
        return "$staticBlock$" + identifier;
    }

    default MethodInfo findStaticBlock(int i) {
        String name = createStaticBlockMethodName(i);
        return methods().stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    default Stream<MethodInfo> staticBlocksRecursively(InspectionProvider inspectionProvider) {
        Stream<MethodInfo> mine = methods().stream().filter(MethodInfo::isStaticBlock);
        Stream<MethodInfo> subTypes = subTypes().stream()
                .flatMap(st -> inspectionProvider.getTypeInspection(st).staticBlocksRecursively(inspectionProvider));
        return Stream.concat(mine, subTypes);
    }

    default Stream<List<MethodInfo>> staticBlocksPerType(InspectionProvider inspectionProvider) {
        List<MethodInfo> mine = methods().stream().filter(MethodInfo::isStaticBlock).toList();
        Stream<List<MethodInfo>> subTypes = subTypes().stream()
                .flatMap(st -> inspectionProvider.getTypeInspection(st).staticBlocksPerType(inspectionProvider));
        return Stream.concat(Stream.of(mine), subTypes);
    }

    interface Builder extends InspectionBuilder<Builder>, TypeInspection {

        void computeAccess(InspectionProvider inspectionProvider);

        TypeInspection build(InspectionProvider inspectionProvider);

        @Fluent
        Builder setParentClass(ParameterizedType objectParameterizedType);

        @Fluent
        Builder setTypeNature(TypeNature anInterface);

        @Fluent
        Builder addTypeParameter(TypeParameter typeParameter);

        @Fluent
        Builder addTypeModifier(TypeModifier aPublic);

        @Fluent
        Builder addMethod(MethodInfo methodInfo);

        @Fluent
        Builder setFunctionalInterface(MethodInspection methodInspection);

        @Fluent
        Builder addInterfaceImplemented(ParameterizedType functionalInterfaceType);

        @Fluent
        Builder noParent(Primitives primitives);

        @Fluent
        Builder addField(FieldInfo fieldInfo);

        @Fluent
        Builder addSubType(TypeInfo containerTypeInfo);

        @Fluent
        Builder addConstructor(MethodInfo methodInfo);

        @Fluent
        Builder setEnclosingMethod(MethodInfo enclosingMethod);

        @Modified
        void setAccessFromModifiers();
    }
}
