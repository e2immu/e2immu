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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.*;

import java.util.*;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.*;

public class TypeInspectionImpl extends InspectionImpl implements TypeInspection {
    // the type that this inspection object belongs to
    public final TypeInfo typeInfo;

    public final TypeNature typeNature;

    public final ParameterizedType parentClass;

    public final List<MethodInfo> constructors;
    public final List<MethodInfo> methods;
    public final List<FieldInfo> fields;
    public final Set<TypeModifier> modifiers;
    public final List<TypeInfo> subTypes;
    public final List<TypeParameter> typeParameters;
    public final List<ParameterizedType> interfacesImplemented;
    public final TypeModifier access;

    public final AnnotationMode annotationMode;

    private TypeInspectionImpl(TypeInfo typeInfo,
                               TypeNature typeNature,
                               TypeModifier access,
                               List<TypeParameter> typeParameters,
                               ParameterizedType parentClass,
                               List<ParameterizedType> interfacesImplemented,
                               List<MethodInfo> constructors,
                               List<MethodInfo> methods,
                               List<FieldInfo> fields,
                               Set<TypeModifier> modifiers,
                               List<TypeInfo> subTypes,
                               List<AnnotationExpression> annotations,
                               AnnotationMode annotationMode) {
        super(annotations);
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
    public String toString() {
        return "type inspection of " + typeInfo.fullyQualifiedName;
    }

    @Override
    public TypeInfo typeInfo() {
        return typeInfo;
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
    public Set<TypeModifier> modifiers() {
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
    public InspectionState getInspectionState() {
        return BUILT;
    }

    public enum InspectionState {
        TRIGGER_BYTECODE_INSPECTION(1),
        STARTING_BYTECODE(2),
        FINISHED_BYTECODE(3),
        TRIGGER_JAVA_PARSER(4),
        STARTING_JAVA_PARSER(5),
        FINISHED_JAVA_PARSER(6),
        BY_HAND(7),
        BUILT(8);

        public final int state;

        InspectionState(int state) {
            this.state = state;
        }

        public boolean ge(InspectionState other) {
            return state >= other.state;
        }

        public boolean le(InspectionState other) {
            return state <= other.state;
        }

        public boolean lt(InspectionState other) {
            return state < other.state;
        }
    }

    @Container(builds = TypeInspectionImpl.class)
    public static class Builder extends AbstractInspectionBuilder<Builder> implements TypeInspection {
        private TypeNature typeNature = TypeNature.CLASS;
        private final Set<String> methodAndConstructorNames = new HashSet<>();
        private final List<MethodInfo> methods = new ArrayList<>();
        private final List<MethodInfo> constructors = new ArrayList<>();
        private final List<FieldInfo> fields = new ArrayList<>();
        private final Set<TypeModifier> modifiers = new HashSet<>();
        private final Set<String> subTypeNames = new HashSet<>();
        private final List<TypeInfo> subTypes = new ArrayList<>();
        private final List<TypeParameter> typeParameters = new ArrayList<>();
        private ParameterizedType parentClass;
        private final List<ParameterizedType> interfacesImplemented = new ArrayList<>();
        private final TypeInfo typeInfo;

        private InspectionState inspectionState;

        public Builder(TypeInfo typeInfo, InspectionState inspectionState) {
            this.typeInfo = typeInfo;
            this.inspectionState = inspectionState;
        }

        public boolean finishedInspection() {
            return inspectionState == FINISHED_BYTECODE || inspectionState.ge(FINISHED_JAVA_PARSER);
        }

        public InspectionState getInspectionState() {
            return inspectionState;
        }

        public void setInspectionState(InspectionState inspectionState) {
            this.inspectionState = inspectionState;
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
            if (!methodAndConstructorNames.add(methodInfo.distinguishingName)) {
                throw new UnsupportedOperationException("Already have " + methodInfo.distinguishingName +
                        ", set is " + methodAndConstructorNames);
            }
            constructors.add(methodInfo);
            return this;
        }

        public void ensureConstructor(MethodInfo methodInfo) {
            if (!methodAndConstructorNames.contains(methodInfo.distinguishingName)) {
                constructors.add(methodInfo);
            }
        }

        public Builder addMethod(MethodInfo methodInfo) {
            if (!methodAndConstructorNames.add(methodInfo.distinguishingName)) {
                throw new UnsupportedOperationException("Already have " + methodInfo.distinguishingName
                        + ", set is " + methodAndConstructorNames);
            }
            methods.add(methodInfo);
            return this;
        }

        public void ensureMethod(MethodInfo methodInfo) {
            if (!methodAndConstructorNames.contains(methodInfo.distinguishingName)) {
                methods.add(methodInfo);
            }
        }

        public Builder addSubType(TypeInfo typeInfo) {
            if (!subTypeNames.add(typeInfo.simpleName)) {
                throw new UnsupportedOperationException("Already have subtype " + typeInfo);
            }
            subTypes.add(typeInfo);
            return this;
        }

        public void ensureSubType(TypeInfo typeInfo) {
            if (!subTypeNames.contains(typeInfo.simpleName)) {
                subTypes.add(typeInfo);
            }
        }

        // the iterative parser can call this method multiple times
        public Builder addTypeParameter(TypeParameter typeParameter) {
            if (typeParameter.isMethodTypeParameter()) throw new UnsupportedOperationException();
            if (typeParameter.getIndex() < typeParameters.size()) {
                // we've seen the index before, overwrite
                typeParameters.set(typeParameter.getIndex(), typeParameter);
            } else {
                assert typeParameters.size() == typeParameter.getIndex();
                typeParameters.add(typeParameter);
            }
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
                    getAnnotations(),
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
        public boolean isStatic() {
            return modifiers.contains(TypeModifier.STATIC);
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
        public Set<TypeModifier> modifiers() {
            return ImmutableSet.copyOf(modifiers);
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