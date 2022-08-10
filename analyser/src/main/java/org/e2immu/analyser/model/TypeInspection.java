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
import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.resolver.ShallowMethodResolver;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

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

    @NotNull
    TypeNature typeNature();

    ParameterizedType parentClass();

    //@Immutable(level = 2, after="TypeAnalyser.analyse()")
    @NotNull1
    List<MethodInfo> constructors();

    @NotNull1
    List<MethodInfo> methods();

    @NotNull1
    List<FieldInfo> fields();

    @NotNull1
    Set<TypeModifier> modifiers();

    @NotNull1
    List<TypeInfo> subTypes();

    @NotNull1
    List<TypeParameter> typeParameters();

    @NotNull1
    List<ParameterizedType> interfacesImplemented();

    @NotNull
    Inspector inspector();

    /**
     * Returns the types permitted to extend from this type.
     *
     * @return The types permitted to extend from this type. Note that this list is not empty
     * if and only if the type is sealed.
     */
    @NotNull1
    List<TypeInfo> permittedWhenSealed();

    boolean isFunctionalInterface();

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

    @NotNull
    InspectionState getInspectionState();

    /**
     * @param inspectionProvider to be able to inspect super-types
     * @return true when a functional interface
     */
    default boolean computeIsFunctionalInterface(InspectionProvider inspectionProvider) {
        return computeIsFunctionalInterface(inspectionProvider, this, new HashSet<>(), new HashMap<>()) == 1;
    }

    private static int computeIsFunctionalInterface(InspectionProvider inspectionProvider,
                                                    TypeInspection typeInspection,
                                                    Set<MethodInspection> overridden,
                                                    Map<NamedType, ParameterizedType> translationMap) {
        int sum = 0;
        for (MethodInfo methodInfo : typeInspection.methods()) {
            MethodInspection inspection = inspectionProvider.getMethodInspection(methodInfo);
            boolean nonStaticNonDefault = !inspection.isPrivate() && !inspection.isStatic() && !inspection.isDefault() && !inspection.isOverloadOfJLOMethod();
            if (nonStaticNonDefault) {
                if (overridden.stream().noneMatch(override -> isOverrideOf(inspectionProvider, inspection, override, translationMap))) {
                    sum++;
                    overridden.add(inspection);
                }
            } else if (inspection.isDefault()) {
                // can cancel out a method in one of the super types
                overridden.add(inspection);
            }
        }
        // overridden needs to cancel out all of them, individually!
        if (sum <= 1) {
            for (ParameterizedType superInterface : typeInspection.interfacesImplemented()) {
                TypeInspection typeInspectionOfSuperType = inspectionProvider.getTypeInspection(superInterface.typeInfo);
                Map<NamedType, ParameterizedType> map = ShallowMethodResolver.mapOfSuperType(superInterface, inspectionProvider);
                Map<NamedType, ParameterizedType> superMap = new HashMap<>(translationMap);
                superMap.putAll(map);
                sum += computeIsFunctionalInterface(inspectionProvider, typeInspectionOfSuperType, overridden, superMap);
            }
        }
        return sum;
    }

    private static boolean isOverrideOf(InspectionProvider inspectionProvider,
                                        MethodInspection inSubType,
                                        MethodInspection inSuperType,
                                        Map<NamedType, ParameterizedType> map) {
        if (!inSubType.getMethodInfo().name.equals(inSuperType.getMethodInfo().name)) return false;
        return ShallowMethodResolver.sameParameters(inspectionProvider, inSubType.getParameters(), inSuperType.getParameters(), map);
    }

    /**
     * This is the starting place to compute all types that are referred to in any way.
     *
     * @return a map of all types referenced, with the boolean indicating explicit reference somewhere
     */
    default UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                parentClass() == null ? UpgradableBooleanMap.of() : parentClass().typesReferenced(true),
                typeInfo().packageNameOrEnclosingType.isRight() && !isStatic() && !isInterface() ?
                        UpgradableBooleanMap.of(typeInfo().packageNameOrEnclosingType.getRight(), false) :
                        UpgradableBooleanMap.of(),
                interfacesImplemented().stream().flatMap(i -> i.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector()),
                getAnnotations().stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),
                //ti.subTypes.stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),
                methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                        .flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),
                fields().stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),
                subTypes().stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector())
        );
    }

    default boolean isStatic() {
        if (typeInfo().packageNameOrEnclosingType.isLeft()) return true; // independent type
        return typeNature() != TypeNature.CLASS || modifiers().contains(TypeModifier.STATIC); // static sub type
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
            if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
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
        Stream<MethodInfo> mine = methods().stream().filter(m -> inspectionProvider.getMethodInspection(m).isStaticBlock());
        Stream<MethodInfo> subTypes = subTypes().stream()
                .flatMap(st -> inspectionProvider.getTypeInspection(st).staticBlocksRecursively(inspectionProvider));
        return Stream.concat(mine, subTypes);
    }

    default Stream<List<MethodInfo>> staticBlocksPerType(InspectionProvider inspectionProvider) {
        List<MethodInfo> mine = methods().stream().filter(m -> inspectionProvider.getMethodInspection(m).isStaticBlock()).toList();
        Stream<List<MethodInfo>> subTypes = subTypes().stream()
                .flatMap(st -> inspectionProvider.getTypeInspection(st).staticBlocksPerType(inspectionProvider));
        return Stream.concat(Stream.of(mine), subTypes);
    }

    interface Builder extends InspectionBuilder<Builder>, TypeInspection {

        boolean finishedInspection();

        void computeAccess(InspectionProvider inspectionProvider);

        TypeInspection build(InspectionProvider inspectionProvider);

        void setInspectionState(InspectionState startingBytecode);

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
        Builder setFunctionalInterface(boolean b);

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
    }
}
