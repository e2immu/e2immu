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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Either;
import org.e2immu.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeInspectionImpl extends InspectionImpl implements TypeInspection {
    // the type that this inspection object belongs to
    public final TypeInfo typeInfo;

    // when this type is an inner or nested class of an enclosing class
    public final Either<String, TypeInfo> packageNameOrEnclosingType;

    public final TypeNature typeNature;

    public final ParameterizedType parentClass;

    //@Immutable(level = 2, after="TypeAnalyser.analyse()")
    public final List<MethodInfo> constructors;
    public final List<MethodInfo> methods;
    public final List<FieldInfo> fields;
    public final List<TypeModifier> modifiers;
    public final List<TypeInfo> subTypes;
    public final List<TypeParameter> typeParameters;
    public final List<ParameterizedType> interfacesImplemented;

    // only valid for types that have been defined, and empty when not the primary type
    // it does include the primary type itself
    public final List<TypeInfo> allTypesInPrimaryType;
    public final TypeModifier access;

    public final AnnotationMode annotationMode;

    private TypeInspectionImpl(TypeInfo typeInfo,
                               Either<String, TypeInfo> packageNameOrEnclosingType,
                               List<TypeInfo> allTypesInPrimaryType,
                               TypeNature typeNature,
                               List<TypeParameter> typeParameters,
                               ParameterizedType parentClass,
                               List<ParameterizedType> interfacesImplemented,
                               List<MethodInfo> constructors,
                               List<MethodInfo> methods,
                               List<FieldInfo> fields,
                               List<TypeModifier> modifiers,
                               List<TypeInfo> subTypes,
                               List<AnnotationExpression> annotations,
                               AnnotationMode annotationMode) {
        super(annotations);
        this.allTypesInPrimaryType = allTypesInPrimaryType;
        this.packageNameOrEnclosingType = packageNameOrEnclosingType;
        this.parentClass = parentClass;
        this.interfacesImplemented = interfacesImplemented;
        this.typeParameters = typeParameters;
        this.typeInfo = typeInfo;
        this.typeNature = typeNature;
        this.methods = methods;
        this.constructors = constructors;
        this.fields = fields;
        this.modifiers = modifiers;
        this.subTypes = subTypes;
        if (modifiers.contains(TypeModifier.PUBLIC)) access = TypeModifier.PUBLIC;
        else if (modifiers.contains(TypeModifier.PROTECTED)) access = TypeModifier.PROTECTED;
        else if (modifiers.contains(TypeModifier.PRIVATE)) access = TypeModifier.PRIVATE;
        else access = TypeModifier.PACKAGE;
        this.annotationMode = annotationMode;
    }

    @Override
    public TypeInfo typeInfo() {
        return typeInfo;
    }

    @Override
    public Either<String, TypeInfo> packageNameOrEnclosingType() {
        return packageNameOrEnclosingType;
    }

    @Override
    public TypeNature typeNature() {
        return typeNature;
    }

    @Override
    public ParameterizedType parentClass() {
        return parentClass;
    }

    @Override
    public List<MethodInfo> constructors() {
        return constructors;
    }

    @Override
    public List<MethodInfo> methods() {
        return methods;
    }

    @Override
    public List<FieldInfo> fields() {
        return fields;
    }

    @Override
    public List<TypeModifier> modifiers() {
        return modifiers;
    }

    @Override
    public List<TypeInfo> subTypes() {
        return subTypes;
    }

    @Override
    public List<TypeParameter> typeParameters() {
        return typeParameters;
    }

    @Override
    public List<ParameterizedType> interfacesImplemented() {
        return interfacesImplemented;
    }

    @Override
    public List<TypeInfo> allTypesInPrimaryType() {
        return allTypesInPrimaryType;
    }

    @Override
    public TypeModifier access() {
        return access;
    }

    @Override
    public AnnotationMode annotationMode() {
        return annotationMode;
    }

    @Override
    public int getInspectionState() {
        return BUILT;
    }

    public Stream<MethodInfo> methodStream(Methods methodsMode) {
        if (methodsMode.recurse) {
            return Stream.concat(nonRecursiveMethodStream(methodsMode.nonRecursiveVariant),
                    subTypes.stream().flatMap(subType -> subType.typeInspection.get().methodStream(methodsMode)));
        }
        return nonRecursiveMethodStream(methodsMode);
    }

    private Stream<MethodInfo> nonRecursiveMethodStream(Methods methodsMode) {
        if (methodsMode == Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM) return methods.stream();
        return Stream.concat(methods.stream(),
                methodsInFieldInitializers(methodsMode != Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_ARTIFICIAL_SAM));
    }

    public Stream<MethodInfo> constructorStream(Methods methodsMode) {
        if (methodsMode.recurse) {
            return Stream.concat(constructors.stream(), subTypes.stream()
                    .flatMap(subType -> subType.typeInspection.get().constructorStream(methodsMode)));
        }
        return constructors.stream();
    }

    public Stream<MethodInfo> methodsInFieldInitializers(boolean alsoArtificial) {
        return fields.stream()
                .filter(fieldInfo -> fieldInfo.fieldInspection.get().initialiserIsSet())
                .map(fieldInfo -> fieldInfo.fieldInspection.get().getInitialiser())
                .filter(initialiser -> initialiser.implementationOfSingleAbstractMethod() != null && (alsoArtificial || !initialiser.artificial()))
                .map(FieldInspection.FieldInitialiser::implementationOfSingleAbstractMethod);
    }

    public Set<ParameterizedType> explicitTypes() {
        // handles SAMs of fields as well
        Stream<ParameterizedType> methodTypes = methodsAndConstructors(TypeInspectionImpl.Methods.THIS_TYPE_ONLY)
                .flatMap(methodInfo -> methodInfo.explicitTypes().stream());
        Stream<ParameterizedType> fieldTypes = fields.stream().flatMap(fieldInfo -> fieldInfo.explicitTypes().stream());
        return Stream.concat(methodTypes, fieldTypes).collect(Collectors.toSet());
    }

    public static final int CREATED = 0;
    public static final int STARTING_BYTECODE = 1;
    public static final int FINISHED_BYTECODE = 2;
    public static final int STARTING_JAVA_PARSER = 3;
    public static final int FINISHED_JAVA_PARSER = 4;
    public static final int BY_HAND = 5;
    public static final int BUILT = 6;

    @Container(builds = TypeInspectionImpl.class)
    public static class Builder extends AbstractInspectionBuilder<Builder> implements TypeInspection {
        private String packageName;
        private TypeInfo enclosingType;
        private TypeNature typeNature = TypeNature.CLASS;
        private final List<MethodInfo> methods = new ArrayList<>();
        private final List<MethodInfo> constructors = new ArrayList<>();
        private final List<FieldInfo> fields = new ArrayList<>();
        private final List<TypeModifier> modifiers = new ArrayList<>();
        private final List<TypeInfo> subTypes = new ArrayList<>();
        private final List<TypeParameter> typeParameters = new ArrayList<>();
        private ParameterizedType parentClass;
        private final List<ParameterizedType> interfacesImplemented = new ArrayList<>();
        private final TypeInfo typeInfo;

        private int inspectionState;

        public Builder(TypeInfo typeInfo, int inspectionState) {
            this.typeInfo = typeInfo;
            this.inspectionState = inspectionState;
        }

        public int getInspectionState() {
            return inspectionState;
        }

        public void setInspectionState(int inspectionState) {
            this.inspectionState = inspectionState;
        }

        public Builder setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder setEnclosingType(TypeInfo enclosingType) {
            this.enclosingType = enclosingType;
            return this;
        }

        public Builder setTypeNature(TypeNature typeNature) {
            this.typeNature = typeNature;
            return this;
        }

        public Builder setParentClass(ParameterizedType parentClass) {
            this.parentClass = parentClass;
            return this;
        }

        public Builder addField(FieldInfo fieldInfo) {
            fields.add(fieldInfo);
            return this;
        }

        public Builder addTypeModifier(TypeModifier modifier) {
            modifiers.add(modifier);
            return this;
        }

        public Builder addConstructor(MethodInfo methodInfo) {
            constructors.add(methodInfo);
            return this;
        }

        public Builder addMethod(MethodInfo methodInfo) {
            methods.add(methodInfo);
            return this;
        }

        public Builder addSubType(TypeInfo typeInfo) {
            subTypes.add(typeInfo);
            return this;
        }

        public Builder addTypeParameter(TypeParameter typeParameter) {
            typeParameters.add(typeParameter);
            return this;
        }

        public Builder addInterfaceImplemented(ParameterizedType parameterizedType) {
            interfacesImplemented.add(parameterizedType);
            return this;
        }

        public List<ParameterizedType> getInterfacesImplemented() {
            return interfacesImplemented;
        }

        public TypeInspectionImpl build() {
            Objects.requireNonNull(typeNature);
            if (!Primitives.isJavaLangObject(typeInfo)) {
                Objects.requireNonNull(parentClass);
            }
            Either<String, TypeInfo> packageNameOrEnclosingType = packageName == null ? Either.right(enclosingType) : Either.left(packageName);

            return new TypeInspectionImpl(
                    typeInfo,
                    packageNameOrEnclosingType,
                    allTypesInPrimaryType(),
                    typeNature,
                    typeParameters(),
                    parentClass,
                    interfacesImplemented(),
                    constructors(),
                    methods(),
                    fields(),
                    modifiers(),
                    subTypes(),
                    ImmutableList.copyOf(getAnnotations()),
                    annotationMode());
        }

        private static final Set<String> OFFENSIVE_ANNOTATIONS = Set.of(
                Modified.class.getCanonicalName(),
                Nullable.class.getCanonicalName(),
                Dependent.class.getCanonicalName(),
                MutableModifiesArguments.class.getCanonicalName(),
                org.e2immu.annotation.Variable.class.getCanonicalName());

        private AnnotationMode computeAnnotationMode() {
            return annotations.stream()
                    .filter(ae -> OFFENSIVE_ANNOTATIONS.contains(ae.typeInfo().fullyQualifiedName))
                    .map(ae -> AnnotationMode.OFFENSIVE)
                    .findFirst().orElse(AnnotationMode.DEFENSIVE);
        }

        private List<TypeInfo> allTypes(TypeInfo typeInfo) {
            List<TypeInfo> result = new ArrayList<>();
            result.add(typeInfo);
            for (TypeInfo sub : subTypes) {
                recursivelyCollectSubTypes(sub, result);
            }
            return ImmutableList.copyOf(result);
        }

        private void recursivelyCollectSubTypes(TypeInfo typeInfo, List<TypeInfo> result) {
            result.add(typeInfo);
            for (TypeInfo sub : typeInfo.typeInspection.get().subTypes()) {
                recursivelyCollectSubTypes(sub, result);
            }
        }

        @Override
        public TypeInfo typeInfo() {
            return typeInfo;
        }

        @Override
        public Either<String, TypeInfo> packageNameOrEnclosingType() {
            return null;
        }

        @Override
        public TypeNature typeNature() {
            return typeNature;
        }

        @Override
        public ParameterizedType parentClass() {
            return parentClass;
        }

        @Override
        public List<MethodInfo> constructors() {
            return ImmutableList.copyOf(constructors);
        }

        @Override
        public List<MethodInfo> methods() {
            return ImmutableList.copyOf(methods);
        }

        @Override
        public List<FieldInfo> fields() {
            return ImmutableList.copyOf(fields);
        }

        @Override
        public List<TypeModifier> modifiers() {
            return ImmutableList.copyOf(modifiers);
        }

        @Override
        public List<TypeInfo> subTypes() {
            return ImmutableList.copyOf(subTypes);
        }

        @Override
        public List<TypeParameter> typeParameters() {
            return ImmutableList.copyOf(typeParameters);
        }

        @Override
        public List<ParameterizedType> interfacesImplemented() {
            return ImmutableList.copyOf(interfacesImplemented);
        }

        @Override
        public List<TypeInfo> allTypesInPrimaryType() {
            return packageName != null ? allTypes(typeInfo) : List.of();
        }

        @Override
        public TypeModifier access() {
            return null;
        }

        @Override
        public AnnotationMode annotationMode() {
            return computeAnnotationMode();
        }

        @Override
        public Stream<MethodInfo> methodStream(Methods methodsMode) {
            return null;
        }

        @Override
        public Stream<MethodInfo> constructorStream(Methods methodsMode) {
            return null;
        }

        @Override
        public Stream<MethodInfo> methodsInFieldInitializers(boolean alsoArtificial) {
            return null;
        }

        @Override
        public Set<ParameterizedType> explicitTypes() {
            return null;
        }

    }

}
