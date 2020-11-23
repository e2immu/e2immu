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

    public final List<MethodInfo> constructors;
    public final List<MethodInfo> methods;
    public final List<FieldInfo> fields;
    public final List<TypeModifier> modifiers;
    public final List<TypeInfo> subTypes;
    public final List<TypeParameter> typeParameters;
    public final List<ParameterizedType> interfacesImplemented;
    public final TypeModifier access;

    public final AnnotationMode annotationMode;

    private TypeInspectionImpl(TypeInfo typeInfo,
                               Either<String, TypeInfo> packageNameOrEnclosingType,
                               TypeNature typeNature,
                               TypeModifier access,
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
        this.access = access;
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
    
    public static final int TRIGGER_BYTECODE_INSPECTION = 1;
    public static final int STARTING_BYTECODE = 2;
    public static final int FINISHED_BYTECODE = 3;
    public static final int TRIGGER_JAVA_PARSER = 4;
    public static final int STARTING_JAVA_PARSER = 5;
    public static final int FINISHED_JAVA_PARSER = 6;
    public static final int BY_HAND = 7;
    public static final int BUILT = 8;

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

        public boolean finishedInspection() {
            return inspectionState == FINISHED_BYTECODE || inspectionState >= FINISHED_JAVA_PARSER;
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
            this.parentClass = Objects.requireNonNull(parentClass);
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
            if (Primitives.needsParent(typeInfo) && parentClass == null) {
                throw new UnsupportedOperationException("Need a parent class for " + typeInfo.fullyQualifiedName);
            }

            return new TypeInspectionImpl(
                    typeInfo,
                    packageNameOrEnclosingType(),
                    typeNature,
                    access(),
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

        @Override
        public TypeInfo typeInfo() {
            return typeInfo;
        }

        @Override
        public Either<String, TypeInfo> packageNameOrEnclosingType() {
            return packageName == null ? Either.right(enclosingType) : Either.left(packageName);
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
        public TypeModifier access() {
            if (modifiers.contains(TypeModifier.PUBLIC)) return TypeModifier.PUBLIC;
            if (modifiers.contains(TypeModifier.PROTECTED)) return TypeModifier.PROTECTED;
            if (modifiers.contains(TypeModifier.PRIVATE)) return TypeModifier.PRIVATE;
            return TypeModifier.PACKAGE;
        }

        @Override
        public AnnotationMode annotationMode() {
            return computeAnnotationMode();
        }

    }

}
