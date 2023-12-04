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

package org.e2immu.analyser.inspector.impl;

import com.github.javaparser.ast.stmt.BlockStmt;
import org.e2immu.analyser.inspector.AbstractInspectionBuilder;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class MethodInspectionImpl extends InspectionImpl implements MethodInspection {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodInspectionImpl.class);

    private final String fullyQualifiedName;
    private final String distinguishingName;

    private final MethodInfo methodInfo; // backlink, container... will become contextclass+immutable eventually
    private final ParameterizedType returnType; // ContextClass

    private final Block methodBody; // if empty, Block.EMPTY_BLOCK
    private final List<ParameterInfo> parameters;

    // modifiers provided during parsing!
    private final Set<MethodModifier> parsedModifiers;
    private final boolean isSynchronized;
    private final boolean isFinal;

    private final List<TypeParameter> typeParameters;

    private final List<ParameterizedType> exceptionTypes;

    private final Map<CompanionMethodName, MethodInfo> companionMethods;

    private MethodInspectionImpl(MethodInfo methodInfo,
                                 String fullyQualifiedName,
                                 String distinguishingName,
                                 boolean synthetic,
                                 boolean synchronizedMethod,
                                 boolean finalMethod,
                                 Access access,
                                 Comment comment,
                                 Set<MethodModifier> parsedModifiers,
                                 List<ParameterInfo> parameters,
                                 ParameterizedType returnType,
                                 List<AnnotationExpression> annotations,
                                 List<TypeParameter> typeParameters,
                                 List<ParameterizedType> exceptionTypes,
                                 Map<CompanionMethodName, MethodInfo> companionMethods,
                                 Block methodBody) {
        super(annotations, access, comment, synthetic);
        this.fullyQualifiedName = fullyQualifiedName;
        this.distinguishingName = distinguishingName;
        this.companionMethods = companionMethods;
        this.parsedModifiers = parsedModifiers;
        this.methodInfo = methodInfo;
        this.parameters = parameters;
        this.returnType = returnType;
        this.typeParameters = typeParameters;
        this.methodBody = methodBody;
        this.exceptionTypes = exceptionTypes;
        this.isFinal = finalMethod;
        this.isSynchronized = synchronizedMethod;
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
    public Set<MethodModifier> getParsedModifiers() {
        return parsedModifiers;
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

    @Override
    public List<MethodModifier> minimalModifiers() {
        List<MethodModifier> result = new ArrayList<>();
        Access access = getAccess();
        boolean inInterface = methodInfo.typeInfo.isInterface();
        boolean inAnnotation = methodInfo.typeInfo.typeInspection.get().isAnnotation();
        boolean isAbstract = methodInfo.isAbstract();
        boolean isDefault = methodInfo.isDefault();
        if (access != Access.PACKAGE && !(inInterface && (isAbstract || isDefault))) {
            MethodModifier accessModifier = switch (access) {
                case PRIVATE -> MethodModifier.PRIVATE;
                case PUBLIC -> MethodModifier.PUBLIC;
                case PROTECTED -> MethodModifier.PROTECTED;
                default -> throw new UnsupportedOperationException();
            };
            result.add(accessModifier);
        }
        if (inInterface || inAnnotation) {
            if (isDefault) result.add(MethodModifier.DEFAULT);
        } else {
            if (isAbstract) result.add(MethodModifier.ABSTRACT);
        }
        if (methodInfo.isStatic()) result.add(MethodModifier.STATIC);
        if (isFinal) result.add(MethodModifier.FINAL);
        if (isSynchronized) result.add(MethodModifier.SYNCHRONIZED);
        return result;
    }


    @Override
    public boolean isFinal() {
        return isFinal;
    }

    @Override
    public boolean isSynchronized() {
        return isSynchronized;
    }

    private static final int NOT_A_STATIC_BLOCK = -1;

    @Container(builds = MethodInspectionImpl.class)
    public static class Builder extends AbstractInspectionBuilder<MethodInspection.Builder> implements MethodInspection,
            MethodInspection.Builder {
        private final List<ParameterInspection.Builder> parameters = new ArrayList<>();
        private final Set<MethodModifier> modifiers = new HashSet<>();
        private final List<TypeParameter> typeParameters = new ArrayList<>();
        public final TypeInfo owner;
        public final String name;
        public final MethodInfo.MethodType methodType;
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

        public Builder(TypeInfo owner, MethodInfo.MethodType methodType) {
            this(owner.identifier, owner, owner.simpleName, methodType, NOT_A_STATIC_BLOCK);
            assert methodType.isConstructor();
        }

        public Builder(Identifier identifier, TypeInfo owner, MethodInfo.MethodType methodType) {
            this(identifier, owner, owner.simpleName, methodType, NOT_A_STATIC_BLOCK);
            assert methodType.isConstructor();
        }

        /* method */

        public Builder(TypeInfo owner, String name, MethodInfo.MethodType methodType) {
            this(Identifier.generate("method without id"), owner, name, methodType, NOT_A_STATIC_BLOCK);
            assert !methodType.isConstructor();
        }

        public Builder(Identifier identifier, TypeInfo owner, String name, MethodInfo.MethodType methodType) {
            this(identifier, owner, name, methodType, NOT_A_STATIC_BLOCK);
            assert !methodType.isConstructor();
        }

        /* compact constructor */

        public static Builder compactConstructor(Identifier identifier, TypeInfo owner) {
            return new MethodInspectionImpl.Builder(identifier, owner, owner.simpleName,
                    MethodInfo.MethodType.COMPACT_CONSTRUCTOR, NOT_A_STATIC_BLOCK);
        }

        public static Builder syntheticConstructor(TypeInfo owner) {
            return new MethodInspectionImpl.Builder(Identifier.generate("synthetic array constructor"),
                    owner, owner.simpleName, MethodInfo.MethodType.SYNTHETIC_CONSTRUCTOR, NOT_A_STATIC_BLOCK);
        }

        /* static block */

        public static Builder createStaticBlock(Identifier identifier, TypeInfo owner, int staticBlockIdentifier) {
            String name = TypeInspection.createStaticBlockMethodName(staticBlockIdentifier);
            return new MethodInspectionImpl.Builder(identifier, owner, name, MethodInfo.MethodType.STATIC_BLOCK,
                    staticBlockIdentifier);
        }

        private Builder(Identifier identifier,
                        TypeInfo owner,
                        String name,
                        MethodInfo.MethodType methodType,
                        int staticBlockIdentifier) {
            this.identifier = identifier;
            this.owner = owner;
            this.name = name;
            this.methodType = methodType;
            this.staticBlockIdentifier = staticBlockIdentifier;
            if (methodType.isConstructor() || methodType == MethodInfo.MethodType.STATIC_BLOCK) {
                returnType = ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR;
            }
            if (methodType == MethodInfo.MethodType.DEFAULT_METHOD) {
                modifiers.add(MethodModifier.DEFAULT);
            } else if (methodType == MethodInfo.MethodType.ABSTRACT_METHOD) {
                modifiers.add(MethodModifier.ABSTRACT);
            } else if (methodType == MethodInfo.MethodType.STATIC_METHOD) {
                modifiers.add(MethodModifier.STATIC);
            }
        }

        @Override
        public ParameterInspection.Builder newParameterInspectionBuilder(Identifier generate, ParameterizedType concreteTypeOfParameter, String name, int index) {
            return new ParameterInspectionImpl.Builder(generate, concreteTypeOfParameter, name, index);
        }

        public MethodInfo.MethodType getMethodType() {
            return methodType;
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

        public ParameterInspection.Builder newParameterInspectionBuilder(Identifier identifier, int index) {
            ParameterInspection.Builder builder = new ParameterInspectionImpl.Builder(identifier).setIndex(index);
            addParameter(builder);
            return builder;
        }

        // finally, this one is used by hand

        @Fluent
        public Builder addParameter(@NotNull ParameterInspection.Builder builder) {
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
        public MethodInspection.Builder addTypeParameter(@NotNull TypeParameter typeParameter) {
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

        public MethodInspection.Builder addCompanionMethods(Map<CompanionMethodName, Builder> companionMethods) {
            this.companionMethods.putAll(companionMethods);
            return this;
        }

        @NotModified
        @NotNull
        public MethodInspectionImpl build(InspectionProvider inspectionProvider) {
            if (inspectedBlock == null) {
                inspectedBlock = Block.emptyBlock(Identifier.generate("empty method block"));
            }

            // all companion methods have to have been built already!
            companionMethods.values().forEach(builder -> {
                assert builder.methodInfo() != null && builder.methodInfo().methodInspection.isSet();
            });

            // removed a check that the type parameter, if it belonged to a method, had to be this method.
            // that's not correct, lambdas can have a method parameter type belonging to the enclosing method.
            // we cannot easily check for that because anonymous types cannot (ATM) refer to their owning field/method.

            if (fullyQualifiedName == null) readyToComputeFQN(inspectionProvider);

            makeParametersImmutable();

            // we have a method object now...
            Objects.requireNonNull(returnType);

            if (accessNotYetComputed()) computeAccess(inspectionProvider);

            MethodInspectionImpl methodInspection = new MethodInspectionImpl(methodInfo,
                    getFullyQualifiedName(), // the builders have not been invalidated yet
                    getDistinguishingName(),
                    isSynthetic(),
                    isSynchronized(),
                    isFinal(),
                    getAccess(),
                    getComment(),
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
            LOGGER.debug("Setting inspection of {}", methodInfo.fullyQualifiedName);
            return methodInspection;
        }

        @Override
        public MethodInfo methodInfo() {
            return methodInfo;
        }

        @Override
        public TypeInfo owner() {
            return owner;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isConstructor() {
            return methodType.isConstructor();
        }

        public void readyToComputeFQN(InspectionProvider inspectionProvider) {
            try {
                fullyQualifiedName = owner.fullyQualifiedName + "." + name + "(" + parameters.stream()
                        .map(p -> p.getParameterizedType().printForMethodFQN(inspectionProvider, p.isVarArgs(), Diamond.SHOW_ALL))
                        .collect(Collectors.joining(",")) + ")";
            } catch (RuntimeException re) {
                LOGGER.error("Cannot compute fully qualified method name, type {}, method {}, {} params",
                        owner.fullyQualifiedName, name, parameters.size());
                throw re;
            }
            // see Constructor_16, we must change the name
            String methodName = methodType.isConstructor() ? "<init>" : name;
            distinguishingName = owner.fullyQualifiedName + "." + methodName + "(" + parameters.stream()
                    .map(p -> p.getParameterizedType().distinguishingName(inspectionProvider, p.isVarArgs()))
                    .collect(Collectors.joining(",")) + ")";
            this.methodInfo = new MethodInfo(identifier, owner, name, fullyQualifiedName, distinguishingName, methodType);
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
        public Set<MethodModifier> getParsedModifiers() {
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
            return companionMethods.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().methodInfo()));
        }

        public boolean inspectedBlockIsSet() {
            return inspectedBlock != null;
        }

        public boolean isDefault() {
            return methodType == MethodInfo.MethodType.DEFAULT_METHOD;
        }

        @Override
        public boolean isVarargs() {
            if (parameters.isEmpty()) return false;
            return parameters.get(parameters.size() - 1).isVarArgs();
        }

        @Override
        public boolean isStatic() {
            return methodType == MethodInfo.MethodType.STATIC_METHOD || methodType == MethodInfo.MethodType.STATIC_BLOCK;
        }

        public List<ParameterInspection.Builder> getParameterBuilders() {
            return parameters;
        }

        public boolean isVoid() {
            assert returnType != null : "Method " + fullyQualifiedName + " has no return type yet";
            return returnType.isVoidOrJavaLangVoid();
        }

        /*
        Copy data from a parent method during the shallow inspection process.
         */
        public void copyFrom(MethodInspection parent) {
            returnType = parent.getReturnType();
            modifiers.addAll(parent.getParsedModifiers());
            exceptionTypes.addAll(parent.getExceptionTypes());
            setAccess(parent.getAccess());
        }

        @Override
        public boolean isSynchronized() {
            return modifiers.contains(MethodModifier.SYNCHRONIZED);
        }

        @Override
        public boolean isFinal() {
            return modifiers.contains(MethodModifier.FINAL);
        }

        public boolean isAbstract() {
            return methodType == MethodInfo.MethodType.ABSTRACT_METHOD;
        }

        /*
        can only be done successfully when the access of the enclosing type(s) has been determined!
         */
        @Override
        public Builder computeAccess(InspectionProvider inspectionProvider) {
            if (methodType == MethodInfo.MethodType.COMPACT_CONSTRUCTOR) {
                setAccess(Access.PUBLIC);
            } else if (modifiers.contains(MethodModifier.PRIVATE)) {
                setAccess(Access.PRIVATE);
            } else {
                TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);
                boolean isInterface = typeInspection.isInterface();
                if (isInterface && (isAbstract() || isDefault() || isStatic())) {
                    setAccess(Access.PUBLIC);
                } else {
                    Access fromModifier = accessFromMethodModifier();
                    setAccess(fromModifier);
                }
            }
            return this;
        }

        private Access accessFromMethodModifier() {
            if (modifiers.contains(MethodModifier.PUBLIC)) return Access.PUBLIC;
            if (modifiers.contains(MethodModifier.PRIVATE)) return Access.PRIVATE;
            if (modifiers.contains(MethodModifier.PROTECTED)) return Access.PROTECTED;
            return Access.PACKAGE;
        }

        @Override
        public List<MethodModifier> minimalModifiers() {
            throw new UnsupportedOperationException();
        }
    }
}
