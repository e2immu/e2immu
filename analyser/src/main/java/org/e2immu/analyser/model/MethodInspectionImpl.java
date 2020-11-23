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

import com.github.javaparser.ast.stmt.BlockStmt;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

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
    private final List<MethodModifier> modifiers;

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

    private MethodInspectionImpl(MethodInfo methodInfo,
                                 String fullyQualifiedName,
                                 String distinguishingName,
                                 List<MethodModifier> modifiers,
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
    public List<MethodModifier> getModifiers() {
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

    @Container(builds = MethodInspectionImpl.class)
    public static class Builder extends AbstractInspectionBuilder<Builder> implements MethodInspection {
        private final MethodInfo methodInfo;
        private final List<ParameterInfo> parameters = new ArrayList<>();
        private final List<MethodModifier> modifiers = new ArrayList<>();
        private final List<AnnotationExpression> annotations = new ArrayList<>();
        private final List<TypeParameter> typeParameters = new ArrayList<>();
        private final List<MethodInfo> implementationsOf = new ArrayList<>();
        private final Map<CompanionMethodName, Builder> companionMethods = new LinkedHashMap<>();
        private BlockStmt block;
        private Block inspectedBlock;
        private final List<ParameterizedType> exceptionTypes = new ArrayList<>();
        private ParameterizedType returnType;
        private final Map<Integer, ParameterInspectionImpl.Builder> parameterInspectionBuilders = new HashMap<>();
        private String fullyQualifiedName;
        private String distinguishingName;

        public Builder(MethodInfo methodInfo) {
            this.methodInfo = methodInfo;
        }

        @Fluent
        public Builder setReturnType(ParameterizedType returnType) {
            this.returnType = returnType;
            return this;
        }

        @Fluent
        public Builder setReturnType(@NotNull TypeInfo returnType) {
            this.returnType = returnType.asParameterizedType();
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
            ParameterInspectionImpl.Builder builder = new ParameterInspectionImpl.Builder();
            parameterInspectionBuilders.put(index, builder);
            return builder;
        }

        public void addParameterNoBuilder(ParameterInfo parameterInfo) {
            parameters.add(parameterInfo);
            assert parameterInfo.index + 1 == parameters.size();
        }

        // the following method is used by the method inspector

        public ParameterInspectionImpl.Builder addParameterCreateBuilder(@NotNull ParameterInfo parameterInfo) {
            parameters.add(parameterInfo);
            return newParameterInspectionBuilder(parameterInfo.index);
        }

        // finally, this one is used by hand

        public Builder addParameterFluently(@NotNull ParameterInfo parameterInfo) {
            addParameterCreateBuilder(parameterInfo);
            return this;
        }

        @Fluent
        public Builder addParameters(@NotNull Collection<ParameterInfo> parameters) {
            parameters.forEach(this::addParameterCreateBuilder);
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
        public Builder addTypeParameter(@NotNull TypeParameter typeParameter) {
            typeParameters.add(typeParameter);
            if (!typeParameter.isMethodTypeParameter()) throw new IllegalArgumentException();
            return this;
        }

        @Fluent
        public Builder addCompanionMethods(Map<CompanionMethodName, Builder> companionMethods) {
            this.companionMethods.putAll(companionMethods);
            return this;
        }

        @NotModified
        @NotNull
        public MethodInspectionImpl build() {
            if (methodInfo.isConstructor) {
                returnType = ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR;
            } else {
                Objects.requireNonNull(returnType);
            }
            if (inspectedBlock == null) {
                inspectedBlock = Block.EMPTY_BLOCK;
            }
            for (TypeParameter typeParameter : typeParameters) {
                if (typeParameter.owner.isRight() && typeParameter.owner.getRight() != methodInfo) {
                    throw new UnsupportedOperationException("I cannot have type parameters owned by another method!");
                }
            }

            parameterInspectionBuilders.forEach((index, builder) -> {
                ParameterInfo parameterInfo = parameters.get(index);
                parameterInfo.parameterInspection.set(builder.build());
            });
            companionMethods.values().forEach(builder -> builder.methodInfo.methodInspection.set(builder.build()));

            // removed a check that the type parameter, if it belonged to a method, had to be this method.
            // that's not correct, lambdas can have a method parameter type belonging to the enclosing method.
            // we cannot easily check for that because anonymous types cannot (ATM) refer to their owning field/method.

            if (fullyQualifiedName == null) readyToComputeFQN();

            return new MethodInspectionImpl(methodInfo,
                    getFullyQualifiedName(), // the builders have not been invalidated yet
                    getDistinguishingName(),
                    ImmutableList.copyOf(modifiers),
                    ImmutableList.copyOf(parameters),
                    returnType,
                    ImmutableList.copyOf(annotations),
                    ImmutableList.copyOf(typeParameters),
                    ImmutableList.copyOf(exceptionTypes),
                    ImmutableList.copyOf(implementationsOf),
                    ImmutableMap.copyOf(getCompanionMethods()),
                    inspectedBlock
            );
        }

        public void readyToComputeFQN() {
            this.fullyQualifiedName = methodInfo.typeInfo.fullyQualifiedName + "." + methodInfo.name + "(" + parameters.stream()
                    .map(p -> p.parameterizedType.stream(parameterInspectionBuilders.get(p.index).isVarArgs()))
                    .collect(Collectors.joining(",")) + ")";
            this.distinguishingName = methodInfo.typeInfo.fullyQualifiedName + "." + methodInfo.name + "(" + parameters.stream()
                    .map(p -> p.parameterizedType.distinguishingStream(parameterInspectionBuilders.get(p.index).isVarArgs()))
                    .collect(Collectors.joining(",")) + ")";
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
            return parameters;
        }

        @Override
        public List<MethodModifier> getModifiers() {
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
    }
}
