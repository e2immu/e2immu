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

import com.github.javaparser.ast.stmt.BlockStmt;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECT;
import static org.e2immu.analyser.util.Logger.log;

public class MethodInspectionImpl extends InspectionImpl implements MethodInspection {

    private final String fullyQualifiedName;
    private final String distinguishingName;

    private final MethodInfo methodInfo; // backlink, container... will become contextclass+immutable eventually
    private final ParameterizedType returnType; // ContextClass
    //@Immutable(after="??")
    private final Block methodBody; // if empty, Block.EMPTY_BLOCK

    //@Immutable(level = 2, after="MethodAnalyzer.analyse()")
    //@Immutable
    private final List<ParameterInfo> parameters;
    //@Immutable
    private final Set<MethodModifier> modifiers;

    //@Immutable
    private final List<TypeParameter> typeParameters;
    //@Immutable
    private final List<ParameterizedType> exceptionTypes;

    // if our type implements a number of interfaces, then the method definitions in these interfaces
    // that this method implements, are represented in this variable
    // this is used to check inherited annotations on methods
    //@Immutable
    private final List<MethodInfo> implementationOf;
    private final Map<CompanionMethodName, MethodInfo> companionMethods;
    private final boolean isStatic;
    private final boolean isDefault;

    private MethodInspectionImpl(MethodInfo methodInfo,
                                 String fullyQualifiedName,
                                 String distinguishingName,
                                 boolean isStatic,
                                 boolean isDefault,
                                 Set<MethodModifier> modifiers,
                                 List<ParameterInfo> parameters,
                                 ParameterizedType returnType,
                                 List<AnnotationExpression> annotations,
                                 List<TypeParameter> typeParameters,
                                 List<ParameterizedType> exceptionTypes,
                                 List<MethodInfo> implementationOf,
                                 Map<CompanionMethodName, MethodInfo> companionMethods,
                                 Block methodBody) {
        super(annotations);
        this.fullyQualifiedName = fullyQualifiedName;
        this.distinguishingName = distinguishingName;
        this.companionMethods = companionMethods;
        this.modifiers = modifiers;
        this.methodInfo = methodInfo;
        this.parameters = parameters;
        this.returnType = returnType;
        this.typeParameters = typeParameters;
        this.methodBody = methodBody;
        this.exceptionTypes = exceptionTypes;
        this.implementationOf = implementationOf;
        this.isDefault = isDefault;
        this.isStatic = isStatic;
    }

    @Override
    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    @Override
    public String getDistinguishingName() {
        return distinguishingName;
    }

    @Override
    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    @Override
    public ParameterizedType getReturnType() {
        return returnType;
    }

    @Override
    public Block getMethodBody() {
        return methodBody;
    }

    @Override
    public List<ParameterInfo> getParameters() {
        return parameters;
    }

    @Override
    public Set<MethodModifier> getModifiers() {
        return modifiers;
    }

    @Override
    public List<TypeParameter> getTypeParameters() {
        return typeParameters;
    }

    @Override
    public List<ParameterizedType> getExceptionTypes() {
        return exceptionTypes;
    }

    @Override
    public List<MethodInfo> getImplementationOf() {
        return implementationOf;
    }

    @Override
    public Map<CompanionMethodName, MethodInfo> getCompanionMethods() {
        return companionMethods;
    }

    @Override
    public String toString() {
        return "methodInspection of " + fullyQualifiedName;
    }

    @Override
    public boolean isVarargs() {
        if (getParameters().isEmpty()) return false;
        return getParameters().get(getParameters().size() - 1).parameterInspection.get().isVarArgs();
    }

    @Container(builds = MethodInspectionImpl.class)
    public static class Builder extends AbstractInspectionBuilder<Builder> implements MethodInspection {
        private final List<ParameterInspectionImpl.Builder> parameters = new ArrayList<>();
        private final Set<MethodModifier> modifiers = new HashSet<>();
        private final List<TypeParameter> typeParameters = new ArrayList<>();
        private final List<MethodInfo> implementationsOf = new ArrayList<>();
        public final TypeInfo owner;
        public final String name;
        public final boolean isConstructor;
        private Boolean isStatic;
        private Boolean isDefault;

        private final Map<CompanionMethodName, Builder> companionMethods = new LinkedHashMap<>();
        private BlockStmt block;
        private Block inspectedBlock;
        private final List<ParameterizedType> exceptionTypes = new ArrayList<>();
        private ParameterizedType returnType;
        private String fullyQualifiedName;
        private String distinguishingName;
        private MethodInfo methodInfo;
        private List<ParameterInfo> immutableParameters;

        public Builder(TypeInfo owner, String name) {
            this.owner = owner;
            this.name = name;
            this.isConstructor = false;
        }

        public Builder(TypeInfo owner) {
            this.owner = owner;
            this.name = owner.simpleName;
            this.isConstructor = true;
        }

        @Fluent
        public Builder setStatic(boolean isStatic) {
            this.isStatic = isStatic;
            return this;
        }

        @Fluent
        public Builder setDefault(boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        @Fluent
        public Builder setReturnType(ParameterizedType returnType) {
            this.returnType = returnType;
            return this;
        }

        public BlockStmt getBlock() {
            return block;
        }

        @Fluent
        public Builder setBlock(BlockStmt block) {
            this.block = block;
            return this;
        }

        @Fluent
        public Builder setInspectedBlock(Block inspectedBlock) {
            this.inspectedBlock = inspectedBlock;
            return this;
        }

        // the following two methods are used by the bytecode inspector

        public ParameterInspectionImpl.Builder newParameterInspectionBuilder(int index) {
            ParameterInspectionImpl.Builder builder = new ParameterInspectionImpl.Builder().setIndex(index);
            addParameter(builder);
            return builder;
        }

        // finally, this one is used by hand

        @Fluent
        public Builder addParameter(@NotNull ParameterInspectionImpl.Builder builder) {
            if (builder.getIndex() == -1) builder.setIndex(parameters.size());
            parameters.add(builder);
            assert builder.getIndex() == parameters.size() - 1;
            return this;
        }

        @Fluent
        public Builder addParameters(@NotNull Collection<ParameterInspectionImpl.Builder> parameters) {
            parameters.forEach(this::addParameter);
            return this;
        }

        @Fluent
        public Builder addModifier(@NotNull MethodModifier methodModifier) {
            modifiers.add(methodModifier);
            return this;
        }

        @Fluent
        public Builder addExceptionType(@NotNull ParameterizedType exceptionType) {
            exceptionTypes.add(exceptionType);
            return this;
        }

        @Fluent
        public Builder addTypeParameter(@NotNull TypeParameterImpl typeParameter) {
            if (!typeParameter.isMethodTypeParameter()) throw new IllegalArgumentException();
            if (typeParameter.getIndex() < typeParameters.size()) {
                // we've seen the index before, overwrite
                typeParameters.set(typeParameter.getIndex(), typeParameter);
            } else {
                assert typeParameters.size() == typeParameter.getIndex();
                typeParameters.add(typeParameter);
            }
            return this;
        }

        @Fluent
        public Builder addCompanionMethods(Map<CompanionMethodName, Builder> companionMethods) {
            this.companionMethods.putAll(companionMethods);
            return this;
        }

        @NotModified
        @NotNull
        public MethodInspectionImpl build(InspectionProvider inspectionProvider) {
            if (inspectedBlock == null) {
                inspectedBlock = Block.EMPTY_BLOCK;
            }

            // all companion methods have to have been built already!
            companionMethods.values().forEach(builder -> {
                assert builder.methodInfo != null && builder.methodInfo.methodInspection.isSet();
            });

            // removed a check that the type parameter, if it belonged to a method, had to be this method.
            // that's not correct, lambdas can have a method parameter type belonging to the enclosing method.
            // we cannot easily check for that because anonymous types cannot (ATM) refer to their owning field/method.

            if (fullyQualifiedName == null) readyToComputeFQN(inspectionProvider);

            // we have a method object now...
            if (methodInfo.isConstructor) {
                returnType = ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR;
            } else {
                Objects.requireNonNull(returnType);
            }

            MethodInspectionImpl methodInspection = new MethodInspectionImpl(methodInfo,
                    getFullyQualifiedName(), // the builders have not been invalidated yet
                    getDistinguishingName(),
                    isStatic(),
                    isDefault(),
                    ImmutableSet.copyOf(modifiers),
                    ImmutableList.copyOf(immutableParameters),
                    returnType,
                    getAnnotations(),
                    ImmutableList.copyOf(typeParameters),
                    ImmutableList.copyOf(exceptionTypes),
                    ImmutableList.copyOf(implementationsOf),
                    ImmutableMap.copyOf(getCompanionMethods()),
                    inspectedBlock
            );
            methodInfo.methodInspection.set(methodInspection);
            log(INSPECT, "Setting inspection of {}", methodInfo.fullyQualifiedName);
            return methodInspection;
        }

        public void readyToComputeFQN(InspectionProvider inspectionProvider) {
            fullyQualifiedName = owner.fullyQualifiedName + "." + name + "(" + parameters.stream()
                    .map(p -> p.getParameterizedType().stream(inspectionProvider, p.isVarArgs()))
                    .collect(Collectors.joining(",")) + ")";
            distinguishingName = owner.fullyQualifiedName + "." + name + "(" + parameters.stream()
                    .map(p -> p.getParameterizedType().distinguishingStream(inspectionProvider, p.isVarArgs()))
                    .collect(Collectors.joining(",")) + ")";
            this.methodInfo = new MethodInfo(owner, name, fullyQualifiedName, distinguishingName, isConstructor);
            typeParameters.forEach(tp -> ((TypeParameterImpl) tp).setMethodInfo(methodInfo));
            immutableParameters = parameters.stream()
                    .map(b -> b.build(methodInfo)).sorted().collect(Collectors.toList());
        }

        @Override
        public String getFullyQualifiedName() {
            if (fullyQualifiedName == null) throw new UnsupportedOperationException("Not ready");
            return fullyQualifiedName;
        }

        @Override
        public String getDistinguishingName() {
            if (distinguishingName == null) throw new UnsupportedOperationException("Not ready");
            return distinguishingName;
        }

        @Override
        public MethodInfo getMethodInfo() {
            if (methodInfo == null) throw new UnsupportedOperationException("Not ready");
            return methodInfo;
        }

        @Override
        public ParameterizedType getReturnType() {
            return returnType;
        }

        @Override
        public Block getMethodBody() {
            return inspectedBlock;
        }

        @Override
        public List<ParameterInfo> getParameters() {
            if (immutableParameters == null) throw new UnsupportedOperationException();
            return immutableParameters;
        }

        @Override
        public Set<MethodModifier> getModifiers() {
            return modifiers;
        }

        @Override
        public List<TypeParameter> getTypeParameters() {
            return typeParameters;
        }

        @Override
        public List<ParameterizedType> getExceptionTypes() {
            return exceptionTypes;
        }

        @Override
        public List<MethodInfo> getImplementationOf() {
            return implementationsOf;
        }

        @Override
        public Map<CompanionMethodName, MethodInfo> getCompanionMethods() {
            return companionMethods.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().methodInfo));
        }

        public boolean inspectedBlockIsSet() {
            return inspectedBlock != null;
        }

        @Override
        public boolean isDefault() {
            return isDefault != null ? isDefault : modifiers.contains(MethodModifier.DEFAULT);
        }

        @Override
        public boolean isVarargs() {
            if (parameters.isEmpty()) return false;
            return parameters.get(parameters.size() - 1).isVarArgs();
        }

        @Override
        public boolean isStatic() {
            return isStatic != null ? isStatic : modifiers.contains(MethodModifier.STATIC);
        }
    }
}
