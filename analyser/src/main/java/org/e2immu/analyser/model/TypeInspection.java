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
import org.e2immu.analyser.util.Either;
import org.e2immu.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * all the fields are deeply immutable or in the case of TypeInfo, eventually immutable.
 */
public interface TypeInspection extends Inspection {
    // the type that this inspection object belongs to
    TypeInfo typeInfo();

    // when this type is an inner or nested class of an enclosing class
    Either<String, TypeInfo> packageNameOrEnclosingType();

    TypeNature typeNature();

    ParameterizedType parentClass();

    //@Immutable(level = 2, after="TypeAnalyser.analyse()")
    List<MethodInfo> constructors();
    List<MethodInfo> methods();
    List<FieldInfo> fields();
    List<TypeModifier> modifiers();
    List<TypeInfo> subTypes();
    List<TypeParameter> typeParameters();
    List<ParameterizedType> interfacesImplemented();

    List<TypeInfo> superTypes();

    // only valid for types that have been defined, and empty when not the primary type
    // it does include the primary type itself
    List<TypeInfo> allTypesInPrimaryType();
    TypeModifier access();

    AnnotationMode annotationMode();

    default boolean isClass() {
        return typeNature() == TypeNature.CLASS;
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

    Stream<MethodInfo> methodStream(Methods methodsMode);

    Stream<MethodInfo> constructorStream(Methods methodsMode);
    
    default Iterable<MethodInfo> methods(Methods methodsMode) {
        return () -> methodStream(methodsMode).iterator();
    }

    default Iterable<MethodInfo> methodsAndConstructors() {
        return Iterables.concat(methods(), constructors());
    }

    Stream<MethodInfo> methodsInFieldInitializers(boolean alsoArtificial);

    Set<ParameterizedType> explicitTypes();


    default boolean haveNonStaticNonDefaultMethods() {
        if (methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .anyMatch(m -> !m.isStatic && !m.isDefaultImplementation)) return true;
        for (ParameterizedType superInterface : interfacesImplemented()) {
            assert superInterface.typeInfo != null && superInterface.typeInfo.hasBeenInspected();
            if (superInterface.typeInfo.typeInspection.get().haveNonStaticNonDefaultMethods()) {
                return true;
            }
        }
        return false;
    }
}
