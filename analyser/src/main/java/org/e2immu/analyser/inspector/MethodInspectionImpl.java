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

import com.github.javaparser.ast.stmt.BlockStmt;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECTOR;
import static org.e2immu.analyser.util.Logger.log;

public class MethodInspectionImpl extends InspectionImpl implements MethodInspection {

    enum MethodType {
        CONSTRUCTOR, COMPACT_CONSTRUCTOR, STATIC_BLOCK, DEFAULT_METHOD, STATIC_METHOD, METHOD,
    }

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

    private final Map<CompanionMethodName, MethodInfo> companionMethods;
    private final MethodType methodType;

    private MethodInspectionImpl(MethodInfo methodInfo,
                                 String fullyQualifiedName,
                                 String distinguishingName,
                                 boolean synthetic,
                                 MethodType methodType,
                                 Set<MethodModifier> modifiers,
                                 List<ParameterInfo> parameters,
                                 ParameterizedType returnType,
                                 List<AnnotationExpression> annotations,
                                 List<TypeParameter> typeParameters,
                                 List<ParameterizedType> exceptionTypes,
                                 Map<CompanionMethodName, MethodInfo> companionMethods,
                                 Block methodBody) {
        super(annotations, synthetic);
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
        this.methodType = methodType;
    }

    @Override
    public boolean isCompactConstructor() {
        return methodType == MethodType.COMPACT_CONSTRUCTOR;
    }

    @Override
    public boolean isDefault() {
        return methodType == MethodType.DEFAULT_METHOD;
    }

    @Override
    public boolean isStatic() {
        return methodType == MethodType.STATIC_METHOD || methodType == MethodType.STATIC_BLOCK;
    }

    @Override
    public boolean isStaticBlock() {
        return methodType == MethodType.STATIC_BLOCK;
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

    private static final int NOT_A_STATIC_BLOCK = -1;

    @Container(builds = MethodInspectionImpl.class)
    public static class Builder extends AbstractInspectionBuilder<Builder> implements MethodInspection {
        private final List<ParameterInspectionImpl.Builder> parameters = new ArrayList<>();
        private final Set<MethodModifier> modifiers = new HashSet<>();
        private final List<TypeParameter> typeParameters = new ArrayList<>();
        public final TypeInfo owner;
        public final String name;
        public final boolean isConstructor;
        public final boolean compactConstructor;
        public final int staticBlockIdentifier;
        private final Identifier identifier;

        private final Map<CompanionMethodName, Builder> companionMethods = new LinkedHashMap<>();
        private BlockStmt block;
        private Block inspectedBlock;
        private final List<ParameterizedType> exceptionTypes = new ArrayList<>();
        private ParameterizedType returnType;
        private String fullyQualifiedName;
        private String distinguishingName;
        private MethodInfo methodInfo;
        private List<ParameterInfo> immutableParameters;
        private List<ParameterInfo> mutableParameters;

        /* constructor */

        public Builder(TypeInfo owner) {
            this(Identifier.generate(), owner, owner.simpleName, true, false, NOT_A_STATIC_BLOCK);
        }

        public Builder(Identifier identifier, TypeInfo owner) {
            this(identifier, owner, owner.simpleName, true, false, NOT_A_STATIC_BLOCK);
        }
        /* method */

        public Builder(TypeInfo owner, String name) {
            this(Identifier.generate(), owner, name, false, false, NOT_A_STATIC_BLOCK);
        }

        public Builder(Identifier identifier, TypeInfo owner, String name) {
            this(identifier, owner, name, false, false, NOT_A_STATIC_BLOCK);
        }

        /* compact constructor */

        public static Builder compactConstructor(Identifier identifier, TypeInfo owner) {
            return new Builder(identifier, owner, owner.simpleName, true, true, NOT_A_STATIC_BLOCK);
        }

        /* static block */

        public static Builder createStaticBlock(Identifier identifier, TypeInfo owner, int staticBlockIdentifier) {
            String name = TypeInspection.createStaticBlockMethodName(staticBlockIdentifier);
            return new Builder(identifier, owner, name, false, false, staticBlockIdentifier);
        }

        private Builder(Identifier identifier,
                        TypeInfo owner,
                        String name,
                        boolean isConstructor,
                        boolean isCompact,
                        int staticBlockIdentifier) {
            this.identifier = identifier;
            this.owner = owner;
            this.name = name;
            this.isConstructor = isConstructor;
            this.compactConstructor = isCompact;
            this.staticBlockIdentifier = staticBlockIdentifier;
        }

        @Override
        public boolean isCompactConstructor() {
            return compactConstructor;
        }

        @Override
        public boolean isStaticBlock() {
            return staticBlockIdentifier > NOT_A_STATIC_BLOCK;
        }

        @Fluent
        public Builder setStatic(boolean isStatic) {
            if (isStatic) this.modifiers.add(MethodModifier.STATIC);
            else if (this.modifiers.contains(MethodModifier.STATIC)) throw new UnsupportedOperationException();
            return this;
        }

        @Fluent
        public Builder setDefault(boolean isDefault) {
            if (isDefault) this.modifiers.add(MethodModifier.DEFAULT);
            else if (this.modifiers.contains(MethodModifier.DEFAULT)) throw new UnsupportedOperationException();
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

        public ParameterInspectionImpl.Builder newParameterInspectionBuilder(Identifier identifier, int index) {
            ParameterInspectionImpl.Builder builder = new ParameterInspectionImpl.Builder(identifier).setIndex(index);
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

        public void addCompanionMethods(Map<CompanionMethodName, Builder> companionMethods) {
            this.companionMethods.putAll(companionMethods);
        }

        @NotModified
        @NotNull
        public MethodInspectionImpl build(InspectionProvider inspectionProvider) {
            if (inspectedBlock == null) {
                inspectedBlock = Block.emptyBlock(Identifier.generate());
            }

            // all companion methods have to have been built already!
            companionMethods.values().forEach(builder -> {
                assert builder.methodInfo != null && builder.methodInfo.methodInspection.isSet();
            });

            // removed a check that the type parameter, if it belonged to a method, had to be this method.
            // that's not correct, lambdas can have a method parameter type belonging to the enclosing method.
            // we cannot easily check for that because anonymous types cannot (ATM) refer to their owning field/method.

            if (fullyQualifiedName == null) readyToComputeFQN(inspectionProvider);

            makeParametersImmutable();

            // we have a method object now...
            if (methodInfo.isConstructor || isStaticBlock()) {
                returnType = ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR;
            } else {
                Objects.requireNonNull(returnType);
            }
            MethodType methodType;
            if (isDefault()) {
                methodType = MethodType.DEFAULT_METHOD;
            } else if (isStaticBlock()) {
                methodType = MethodType.STATIC_BLOCK;
            } else if (compactConstructor) {
                methodType = MethodType.COMPACT_CONSTRUCTOR;
            } else if (isStatic()) {
                methodType = MethodType.STATIC_METHOD;
            } else {
                methodType = methodInfo.isConstructor ? MethodType.CONSTRUCTOR : MethodType.METHOD;
            }

            MethodInspectionImpl methodInspection = new MethodInspectionImpl(methodInfo,
                    getFullyQualifiedName(), // the builders have not been invalidated yet
                    getDistinguishingName(),
                    isSynthetic(),
                    methodType,
                    Set.copyOf(modifiers),
                    List.copyOf(immutableParameters),
                    returnType,
                    getAnnotations(),
                    List.copyOf(typeParameters),
                    List.copyOf(exceptionTypes),
                    Map.copyOf(getCompanionMethods()),
                    inspectedBlock
            );
            methodInfo.methodInspection.set(methodInspection);
            log(INSPECTOR, "Setting inspection of {}", methodInfo.fullyQualifiedName);
            return methodInspection;
        }

        public void readyToComputeFQN(InspectionProvider inspectionProvider) {
            fullyQualifiedName = owner.fullyQualifiedName + "." + name + "(" + parameters.stream()
                    .map(p -> p.getParameterizedType().printForMethodFQN(inspectionProvider, p.isVarArgs(), Diamond.SHOW_ALL))
                    .collect(Collectors.joining(",")) + ")";
            distinguishingName = owner.fullyQualifiedName + "." + name + "(" + parameters.stream()
                    .map(p -> p.getParameterizedType().distinguishingName(inspectionProvider, p.isVarArgs()))
                    .collect(Collectors.joining(",")) + ")";
            this.methodInfo = new MethodInfo(identifier, owner, name, fullyQualifiedName, distinguishingName, isConstructor);
            typeParameters.forEach(tp -> ((TypeParameterImpl) tp).setMethodInfo(methodInfo));
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
            if (immutableParameters == null) return getMutableParameters();
            return immutableParameters;
        }

        private List<ParameterInfo> getMutableParameters() {
            MethodInfo localMethodInfo = getMethodInfo();
            if (mutableParameters == null) {
                mutableParameters = parameters.stream()
                        .map(b -> b.build(localMethodInfo)).sorted().collect(Collectors.toList());
            }
            return mutableParameters;
        }

        public void makeParametersImmutable() {
            if (immutableParameters == null) {
                immutableParameters = parameters.stream()
                        .map(b -> b.build(methodInfo)).sorted().collect(Collectors.toList());
            }
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
        public Map<CompanionMethodName, MethodInfo> getCompanionMethods() {
            return companionMethods.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().methodInfo));
        }

        public boolean inspectedBlockIsSet() {
            return inspectedBlock != null;
        }

        @Override
        public boolean isDefault() {
            return modifiers.contains(MethodModifier.DEFAULT);
        }

        @Override
        public boolean isVarargs() {
            if (parameters.isEmpty()) return false;
            return parameters.get(parameters.size() - 1).isVarArgs();
        }

        @Override
        public boolean isStatic() {
            return modifiers.contains(MethodModifier.STATIC) || isStaticBlock();
        }

        public List<ParameterInspectionImpl.Builder> getParameterBuilders() {
            return parameters;
        }

        public boolean isVoid() {
            return Primitives.isVoidOrJavaLangVoid(returnType);
        }

        /*
        Copy data from a parent method during the shallow inspection process.
         */
        public void copyFrom(MethodInspection parent) {
            returnType = parent.getReturnType();
            modifiers.addAll(parent.getModifiers());
            exceptionTypes.addAll(parent.getExceptionTypes());
        }
    }
}
