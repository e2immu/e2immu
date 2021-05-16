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

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.annotation.*;

import java.util.*;
import java.util.stream.Stream;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.*;
import static org.e2immu.analyser.inspector.TypeInspector.PACKAGE_NAME_FIELD;
import static org.e2immu.analyser.util.Logger.LogTarget.INSPECTOR;
import static org.e2immu.analyser.util.Logger.log;

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
    public final List<TypeInfo> permittedWhenSealed;
    public final List<TypeParameter> typeParameters;
    public final List<ParameterizedType> interfacesImplemented;
    public final TypeModifier access;
    public final boolean hasOneKnownGeneratedImplementation;
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
                               List<TypeInfo> permittedWhenSealed,
                               List<AnnotationExpression> annotations,
                               AnnotationMode annotationMode,
                               boolean synthetic,
                               boolean hasOneKnownGeneratedImplementation) {
        super(annotations, synthetic);
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
        this.permittedWhenSealed = permittedWhenSealed;
        this.hasOneKnownGeneratedImplementation = hasOneKnownGeneratedImplementation;
    }

    @Override
    public boolean hasOneKnownGeneratedImplementation() {
        return hasOneKnownGeneratedImplementation;
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

    @Override
    public List<TypeInfo> permittedWhenSealed() {
        return permittedWhenSealed;
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
        private final List<TypeInfo> permittedWhenSealed = new ArrayList<>();
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
        private int countImplementations;
        private int countGeneratedImplementations;

        public Builder(TypeInfo typeInfo, InspectionState inspectionState) {
            this.typeInfo = typeInfo;
            this.inspectionState = inspectionState;
        }

        public void registerImplementation(boolean generated) {
            countImplementations++;
            if (generated) countGeneratedImplementations++;
        }

        @Override
        public List<TypeInfo> permittedWhenSealed() {
            return List.copyOf(permittedWhenSealed);
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

        public boolean hasEmptyConstructorIfNoConstructorsPresent() {
            return typeNature == TypeNature.CLASS || typeNature == TypeNature.ENUM;
        }

        public Builder noParent(Primitives primitives) {
            this.parentClass = primitives.objectParameterizedType;
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

        public Builder addPermitted(TypeInfo typeInfo) {
            assert modifiers.contains(TypeModifier.SEALED);
            permittedWhenSealed.add(typeInfo);
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
            assert permittedWhenSealed.isEmpty() || modifiers.contains(TypeModifier.SEALED);
            assert !modifiers.contains(TypeModifier.SEALED) || !permittedWhenSealed.isEmpty();

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
                    permittedWhenSealed(),
                    getAnnotations(),
                    annotationMode(),
                    isSynthetic(),
                    countGeneratedImplementations == 1 && countImplementations == 1);
        }

        private static final Set<String> GREEN_MODE_ANNOTATIONS_ON_METHODS = Set.of(
                Modified.class.getCanonicalName(),
                Dependent.class.getCanonicalName());
        private static final Set<String> GREEN_MODE_ANNOTATIONS_ON_FIELDS = Set.of(
                Variable.class.getCanonicalName());
        private static final Set<String> GREEN_MODE_ANNOTATIONS_ON_TYPES = Set.of(
                org.e2immu.annotation.MutableModifiesArguments.class.getCanonicalName());

        private AnnotationMode computeAnnotationMode() {
            boolean haveGreenModeAnnotation = methodsAndConstructors().stream()
                    .filter(methodInfo -> methodInfo.methodInspection.isSet())
                    .flatMap(methodInfo -> methodInfo.methodInspection.get().getAnnotations().stream())
                    .anyMatch(ae -> GREEN_MODE_ANNOTATIONS_ON_METHODS.contains(ae.typeInfo().fullyQualifiedName)) ||
                    fields().stream()
                            .filter(fieldInfo -> fieldInfo.fieldInspection.isSet())
                            .flatMap(fieldInfo -> fieldInfo.fieldInspection.get().getAnnotations().stream())
                            .anyMatch(ae -> GREEN_MODE_ANNOTATIONS_ON_FIELDS.contains(ae.typeInfo().fullyQualifiedName)) ||
                    Stream.concat(annotations.stream(), subTypes.stream()
                            .filter(sub -> sub.typeInspection.isSet())
                            .flatMap(sub -> sub.typeInspection.get().getAnnotations().stream()))
                            .anyMatch(ae -> GREEN_MODE_ANNOTATIONS_ON_TYPES.contains(ae.typeInfo().fullyQualifiedName));

            return haveGreenModeAnnotation ? AnnotationMode.RED : AnnotationMode.GREEN;
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
            return List.copyOf(constructors);
        }

        @Override
        public List<MethodInfo> methods() {
            return List.copyOf(methods);
        }

        @Override
        public List<FieldInfo> fields() {
            return List.copyOf(fields);
        }

        @Override
        public Set<TypeModifier> modifiers() {
            return Set.copyOf(modifiers);
        }

        @Override
        public List<TypeInfo> subTypes() {
            return List.copyOf(subTypes);
        }

        @Override
        public List<TypeParameter> typeParameters() {
            return List.copyOf(typeParameters);
        }

        @Override
        public List<ParameterizedType> interfacesImplemented() {
            return List.copyOf(interfacesImplemented);
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


        public void recursivelyAddToTypeStore(boolean parentIsPrimaryType,
                                              boolean parentIsDollarType,
                                              TypeMapImpl.Builder typeStore,
                                              TypeDeclaration<?> typeDeclaration) {
            typeDeclaration.getMembers().forEach(bodyDeclaration -> bodyDeclaration.ifTypeDeclaration(cid -> {
                TypeInspector.DollarResolverResult res = subTypeInfo(typeInfo, cid.getName().asString(),
                        typeDeclaration, parentIsPrimaryType, parentIsDollarType);

                TypeInspectionImpl.InspectionState inspectionState = res.isDollarType() ? TRIGGER_BYTECODE_INSPECTION :
                        STARTING_JAVA_PARSER;
                TypeInspectionImpl.Builder subTypeBuilder = typeStore.ensureTypeAndInspection(res.subType(), inspectionState);
                if (!res.isDollarType()) {
                    addSubType(subTypeBuilder.typeInfo());
                }
                log(INSPECTOR, "Added {} to type store: {}", cid.getClass().getSimpleName(), res.subType().fullyQualifiedName);

                subTypeBuilder.recursivelyAddToTypeStore(false, res.isDollarType(), typeStore, cid);
            }));
        }
    }


    /* the following three methods are part of the annotated API system.
    Briefly, if a first-level subtype's name ends with a $, its FQN is composed by the PACKAGE_NAME field in the primary type
    and the subtype name without the $.
     */
    private static TypeInspector.DollarResolverResult subTypeInfo(TypeInfo enclosingType, String simpleName,
                                                                  TypeDeclaration<?> typeDeclaration,
                                                                  boolean isPrimaryType,
                                                                  boolean parentIsDollarType) {
        if (simpleName.endsWith("$")) {
            if (!isPrimaryType) throw new UnsupportedOperationException();
            String packageName = packageName(typeDeclaration.getFieldByName(PACKAGE_NAME_FIELD).orElse(null));
            if (packageName != null) {
                TypeInfo dollarType = new TypeInfo(packageName, simpleName.substring(0, simpleName.length() - 1));
                return new TypeInspector.DollarResolverResult(dollarType, true);
            }
        }
        return new TypeInspector.DollarResolverResult(new TypeInfo(enclosingType, simpleName), parentIsDollarType);
    }

    static String packageName(FieldDeclaration packageNameField) {
        if (packageNameField != null) {
            if (packageNameField.isFinal() && packageNameField.isStatic()) {
                Optional<Expression> initializer = packageNameField.getVariable(0).getInitializer();
                if (initializer.isPresent()) {
                    return initializer.get().asStringLiteralExpr().getValue();
                }
            }
        }
        return null;
    }
}
