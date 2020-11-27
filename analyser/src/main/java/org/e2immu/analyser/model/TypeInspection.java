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

import com.google.common.collect.Iterables;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.AnnotationMode;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * all the fields are deeply immutable or in the case of TypeInfo, eventually immutable.
 */
public interface TypeInspection extends Inspection {
    // the type that this inspection object belongs to
    TypeInfo typeInfo();

    TypeNature typeNature();

    ParameterizedType parentClass();

    //@Immutable(level = 2, after="TypeAnalyser.analyse()")
    List<MethodInfo> constructors();

    List<MethodInfo> methods();

    List<FieldInfo> fields();

    Set<TypeModifier> modifiers();

    List<TypeInfo> subTypes();

    List<TypeParameter> typeParameters();

    List<ParameterizedType> interfacesImplemented();

    TypeModifier access();

    AnnotationMode annotationMode();

    default boolean isClass() {
        return typeNature() == TypeNature.CLASS;
    }

    default boolean isFunctionalInterface() {
        if (typeNature() != TypeNature.INTERFACE) {
            return false;
        }
        return getAnnotations().stream().anyMatch(ann -> Primitives.isFunctionalInterfaceAnnotation(ann.typeInfo()));
    }

    enum Methods {

        THIS_TYPE_ONLY(false, null),
        THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM(false, null),
        THIS_TYPE_ONLY_EXCLUDE_FIELD_ARTIFICIAL_SAM(false, null),
        INCLUDE_SUBTYPES(true, THIS_TYPE_ONLY);

        Methods(boolean recurse, Methods nonRecursiveVariant) {
            this.recurse = recurse;
            this.nonRecursiveVariant = nonRecursiveVariant;
        }

        boolean recurse;
        Methods nonRecursiveVariant;
    }

    default Stream<MethodInfo> methodsAndConstructors(Methods methodsMode) {
        return Stream.concat(constructorStream(methodsMode), methodStream(methodsMode));
    }

    default Stream<MethodInfo> methodStream(Methods methodsMode) {
        if (methodsMode.recurse) {
            return Stream.concat(nonRecursiveMethodStream(methodsMode.nonRecursiveVariant),
                    subTypes().stream().flatMap(subType -> subType.typeInspection.get().methodStream(methodsMode)));
        }
        return nonRecursiveMethodStream(methodsMode);
    }

    private Stream<MethodInfo> nonRecursiveMethodStream(Methods methodsMode) {
        if (methodsMode == Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM) return methods().stream();
        return Stream.concat(methods().stream(),
                methodsInFieldInitializers(methodsMode != Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_ARTIFICIAL_SAM));
    }

    default Stream<MethodInfo> constructorStream(Methods methodsMode) {
        if (methodsMode.recurse) {
            return Stream.concat(constructors().stream(), subTypes().stream()
                    .flatMap(subType -> subType.typeInspection.get().constructorStream(methodsMode)));
        }
        return constructors().stream();
    }

    default Iterable<MethodInfo> methods(Methods methodsMode) {
        return () -> methodStream(methodsMode).iterator();
    }

    default Iterable<MethodInfo> methodsAndConstructors() {
        return Iterables.concat(methods(), constructors());
    }

    default Stream<MethodInfo> methodsInFieldInitializers(boolean alsoArtificial) {
        return fields().stream()
                .filter(fieldInfo -> fieldInfo.fieldInspection.get().fieldInitialiserIsSet())
                .map(fieldInfo -> fieldInfo.fieldInspection.get().getFieldInitialiser())
                .filter(initialiser -> initialiser.implementationOfSingleAbstractMethod() != null && (alsoArtificial || !initialiser.artificial()))
                .map(FieldInspection.FieldInitialiser::implementationOfSingleAbstractMethod);
    }

    default Set<ParameterizedType> explicitTypes() {
        // handles SAMs of fields as well
        Stream<ParameterizedType> methodTypes = methodsAndConstructors(TypeInspectionImpl.Methods.THIS_TYPE_ONLY)
                .flatMap(methodInfo -> methodInfo.explicitTypes().stream());
        Stream<ParameterizedType> fieldTypes = fields().stream().flatMap(fieldInfo -> fieldInfo.explicitTypes().stream());
        return Stream.concat(methodTypes, fieldTypes).collect(Collectors.toSet());
    }

    TypeInspectionImpl.InspectionState getInspectionState();

    default boolean haveNonStaticNonDefaultMethods() {
        if (methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .anyMatch(m -> !m.methodInspection.get().isStatic() && !m.methodInspection.get().isDefault()))
            return true;
        for (ParameterizedType superInterface : interfacesImplemented()) {
            assert superInterface.typeInfo != null && superInterface.typeInfo.hasBeenInspected();
            if (superInterface.typeInfo.typeInspection.get().haveNonStaticNonDefaultMethods()) {
                return true;
            }
        }
        return false;
    }

    /**
     * This is the starting place to compute all types that are referred to in any way.
     * It is different from imports, because imports need an explicitly written type.
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
                fields().stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector())
        );
    }

    default boolean isStatic() {
        if (typeInfo().packageNameOrEnclosingType.isLeft()) return true; // independent type
        return modifiers().contains(TypeModifier.STATIC); // static sub type
    }

    default boolean isInterface() {
        return typeNature() == TypeNature.INTERFACE;
    }

}
