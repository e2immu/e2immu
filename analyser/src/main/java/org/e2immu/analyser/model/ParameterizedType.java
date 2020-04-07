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

import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.e2immu.annotation.NotNull;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ParameterizedType {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterizedType.class);

    public static final ParameterizedType RETURN_TYPE_OF_CONSTRUCTOR = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType NO_TYPE_GIVEN_IN_LAMBDA = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType IMPLICITLY_JAVA_LANG_OBJECT = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType WILDCARD_PARAMETERIZED_TYPE = new ParameterizedType(WildCard.UNBOUND);

    public enum WildCard {
        NONE, UNBOUND, SUPER, EXTENDS;
    }

    @NotNull
    public static ParameterizedType from(TypeContext context, Type type) {
        return from(context, type, WildCard.NONE);
    }

    private static ParameterizedType from(TypeContext context, Type type, WildCard wildCard) {
        Type baseType = type;
        int arrays = 0;
        if (type.isArrayType()) {
            ArrayType arrayType = (ArrayType) type;
            baseType = arrayType.getComponentType();
            arrays = arrayType.getArrayLevel();
        }
        if (baseType.isPrimitiveType()) {
            return new ParameterizedType(Primitives.PRIMITIVES.primitiveByName(baseType.asString()), arrays);
        }
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            if (wildcardType.getExtendedType().isPresent()) {
                // ? extends T
                return from(context, wildcardType.getExtendedType().get(), WildCard.EXTENDS);
            }
            if (wildcardType.getSuperType().isPresent()) {
                // ? super T
                return from(context, wildcardType.getSuperType().get(), WildCard.SUPER);
            }
            return WILDCARD_PARAMETERIZED_TYPE; // <?>
        }
        if ("void".equals(baseType.asString())) return Primitives.PRIMITIVES.voidParameterizedType;

        String name;
        List<ParameterizedType> parameters = new ArrayList<>();
        if (baseType instanceof ClassOrInterfaceType) {
            ClassOrInterfaceType cit = (ClassOrInterfaceType) baseType;
            name = cit.getName().getIdentifier();
            if (cit.getTypeArguments().isPresent()) {
                for (Type typeArgument : cit.getTypeArguments().get()) {
                    ParameterizedType subPt = ParameterizedType.from(context, typeArgument);
                    parameters.add(subPt);
                }
            }
            if (cit.getScope().isPresent()) {
                // first, check for a FQN
                Type scopeType = cit.getScope().get();
                String fqn = scopeType.asString() + "." + name;
                TypeInfo typeInfo = context.getFullyQualified(fqn, false);
                if (typeInfo != null) {
                    return parameters.isEmpty() ? new ParameterizedType(typeInfo, arrays) : new ParameterizedType(typeInfo, parameters);
                }
                ParameterizedType scopePt = from(context, scopeType);
                // name probably is a sub type in scopePt...
                if (scopePt.typeInfo != null && scopePt.typeInfo.typeInspection.isSet()) {
                    Optional<TypeInfo> subType = scopePt.typeInfo.typeInspection.get().subTypes.stream().filter(st -> st.simpleName.equals(name)).findFirst();
                    if (subType.isPresent()) {
                        return parameters.isEmpty() ? new ParameterizedType(subType.get(), arrays) : new ParameterizedType(subType.get(), parameters);
                    }
                    Optional<FieldInfo> field = scopePt.typeInfo.typeInspection.get().fields.stream().filter(f -> f.name.equals(name)).findFirst();
                    if (field.isPresent()) return field.get().type;
                    throw new UnsupportedOperationException("Cannot find " + name + " in " + scopePt);
                }
            }
        } else {
            name = baseType.asString();
        }
        NamedType namedType = context.get(name, false);
        if (namedType instanceof TypeInfo) {
            TypeInfo typeInfo = (TypeInfo) namedType;
            if (parameters.isEmpty()) {
                return new ParameterizedType(typeInfo, arrays);
            }
            return new ParameterizedType(typeInfo, parameters);
        }
        if (namedType instanceof TypeParameter) {
            if (!parameters.isEmpty()) {
                throw new UnsupportedOperationException("??");
            }
            TypeParameter typeParameter = (TypeParameter) namedType;
            return new ParameterizedType(typeParameter, arrays, wildCard);
        }

        throw new UnsupportedOperationException("Unknown type: " + name + " at line "
                + baseType.getBegin() + " of " + baseType.getClass());
    }

    public boolean isTypeParameter() {
        return typeParameter != null;
    }

    public boolean isType() {
        return typeInfo != null;
    }

    public final TypeInfo typeInfo;

    @NotNull
    public final List<ParameterizedType> parameters;
    public final TypeParameter typeParameter;
    public final int arrays;
    public final WildCard wildCard;

    private ParameterizedType(WildCard wildCard) {
        this.wildCard = wildCard;
        if (wildCard != WildCard.UNBOUND && wildCard != WildCard.NONE) throw new UnsupportedOperationException();
        parameters = List.of();
        arrays = 0;
        typeParameter = null;
        typeInfo = null;
    }

    // T[], ? super T
    public ParameterizedType(TypeParameter typeParameter, int arrays, WildCard wildCard) {
        this.typeParameter = Objects.requireNonNull(typeParameter);
        // it is possible that type parameter inspection will be set slightly later, given signatures like
        // <U::Ljava/lang/Comparable<-TU;>;>
        // U is defined, and used before its full inspection is done
        this.parameters = List.of();
        this.typeInfo = null;
        this.arrays = arrays;
        this.wildCard = wildCard;
    }

    // String, Integer[]
    public ParameterizedType(TypeInfo typeInfo, int arrays) {
        this.typeInfo = Objects.requireNonNull(typeInfo); // TODO there used to be no check when arrays = 0???
        this.parameters = List.of();
        this.typeParameter = null;
        this.arrays = arrays;
        this.wildCard = WildCard.NONE;
    }

    // ? extends Number, ? super Number
    public ParameterizedType(TypeInfo typeInfo, WildCard wildCard) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.parameters = List.of();
        this.typeParameter = null;
        this.arrays = 0;
        this.wildCard = wildCard;
        if (wildCard != WildCard.EXTENDS && wildCard != WildCard.SUPER) throw new UnsupportedOperationException();
    }

    // String, Function<R, ? super T>
    public ParameterizedType(TypeInfo typeInfo, List<ParameterizedType> parameters) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.parameters = ImmutableList.copyOf(Objects.requireNonNull(parameters));
        this.typeParameter = null;
        this.arrays = 0;
        this.wildCard = WildCard.NONE;
    }

    // all-in method used by the ParameterizedTypeFactory in byte code inspector
    public ParameterizedType(TypeInfo typeInfo, int arrays, WildCard wildCard, List<ParameterizedType> typeParameters) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.arrays = arrays;
        this.wildCard = wildCard;
        this.parameters = ImmutableList.copyOf(typeParameters);
        this.typeParameter = null;
    }

    @Override
    public String toString() {
        return (isType() ? "Type " : isTypeParameter() ? "Type param " : "") +
                stream(true, false);
    }

    public String stream() {
        return stream(false, false);
    }

    /**
     * Stream for sending the qualified name to the KV store
     *
     * @return the type as a fully qualified name, with type parameters according to the format
     * Tn or Mn, with n the index, and T for type, M for method
     */
    public String distinguishingStream() {
        return stream(true, true);
    }

    public String detailedString() {
        return stream(true, false);
    }

    private String stream(boolean fullyQualified, boolean numericTypeParameters) {
        StringBuilder sb = new StringBuilder();
        switch (wildCard) {
            case UNBOUND:
                sb.append("?");
                break;
            case EXTENDS:
                sb.append("? extends ");
                break;
            case SUPER:
                sb.append("? super ");
                break;
            case NONE:
        }
        if (isTypeParameter()) {
            if (numericTypeParameters) {
                boolean isType = typeParameter.owner.isLeft();
                sb.append(isType ? "T" : "M").append(typeParameter.index);
            } else {
                sb.append(typeParameter.simpleName());
            }
        } else if (isType()) {
            if (fullyQualified)
                sb.append(typeInfo.fullyQualifiedName);
            else
                sb.append(typeInfo.simpleName);
            if (!parameters.isEmpty()) {
                sb.append("<");
                sb.append(parameters.stream()
                        .map(pt -> pt.stream(fullyQualified, numericTypeParameters))
                        .collect(Collectors.joining(", ")));
                sb.append(">");
            }
        }
        sb.append("[]".repeat(arrays));
        return sb.toString();
    }

    public Set<String> imports() {
        Set<String> imports = new HashSet<>();
        if (isType()) {
            if (!typeInfo.isJavaLang()) {
                imports.add(typeInfo.fullyQualifiedName);
            }
            parameters.forEach(p -> imports.addAll(p.imports()));
        }
        return imports;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterizedType that = (ParameterizedType) o;
        return Objects.equals(typeInfo, that.typeInfo) &&
                parameters.equals(that.parameters) &&
                Objects.equals(typeParameter, that.typeParameter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeInfo, parameters, typeParameter);
    }

    public boolean isPrimitive() {
        return typeInfo != null && typeInfo.isPrimitive();
    }

    private static final Set<String> primitiveStringNotVoid = Set.of(
            "int", "double", "float", "boolean", "short", "byte", "char", "long",
            "java.lang.Character", "java.lang.Boolean", "java.lang.Short", "java.lang.Byte",
            "java.lang.Integer", "java.lang.Double", "java.lang.Float", "java.lang.Long",
            "java.lang.String");

    public boolean isVoid() {
        if (typeInfo == null) return false;
        return typeInfo.fullyQualifiedName.equals("void") || typeInfo.fullyQualifiedName.equals("java.lang.Void");
    }

    public boolean isPrimitiveOrStringNotVoid() {
        return typeInfo != null && primitiveStringNotVoid.contains(typeInfo.fullyQualifiedName);
    }

    public Boolean isEffectivelyImmutable(TypeContext typeContext) {
        if (typeInfo != null) return typeInfo.isE2Immutable(typeContext);
        return false;
    }

    public Boolean isContainer(TypeContext typeContext) {
        if (typeInfo != null) return typeInfo.isContainer(typeContext);
        return false;
    }

    // ******************************************************************************************************

    public Map<NamedType, ParameterizedType> typeParameterMap(ParameterizedType concreteType) {
        if (!parameters.isEmpty() && !concreteType.parameters.isEmpty()) {
            ImmutableMap.Builder<NamedType, ParameterizedType> builder = new ImmutableMap.Builder<>();
            for (int i = 0; i < parameters.size(); i++) {
                builder.putAll(parameters.get(i).typeParameterMap(concreteType.parameters.get(i)));
            }
        }
        if (isTypeParameter() && concreteType.isType()) {
            return Map.of(typeParameter, concreteType);
        }
        return Map.of();
    }

    // make a map from the type's abstract parameters to its concrete ones
    public Map<NamedType, ParameterizedType> initialTypeParameterMap() {
        // TODO make recursive
        if (!isType()) return Map.of();
        if (parameters.isEmpty()) return Map.of();
        ParameterizedType originalType = typeInfo.asParameterizedType();
        int i = 0;
        Map<NamedType, ParameterizedType> map = new HashMap<>();
        for (ParameterizedType parameter : originalType.parameters) {
            if (parameter.isTypeParameter()) {
                map.put(parameter.typeParameter, parameters.get(i));
            }
            i++;
        }
        return map;
    }

    public Map<NamedType, ParameterizedType> translateMap(ParameterizedType concreteType, TypeContext typeContext) {
        if (parameters.isEmpty()) {
            if (isTypeParameter())
                return Map.of(this.typeParameter, concreteType);
            return Map.of();
        }
        Map<NamedType, ParameterizedType> res = new HashMap<>();
        boolean iAmFunctionalInterface = isFunctionalInterface(typeContext);
        boolean concreteTypeIsFunctionalInterface = concreteType.isFunctionalInterface(typeContext);
        if (iAmFunctionalInterface != concreteTypeIsFunctionalInterface) {
            throw new UnsupportedOperationException("Have " + iAmFunctionalInterface + " but " + concreteTypeIsFunctionalInterface);
        }
        if (iAmFunctionalInterface) {
            MethodTypeParameterMap methodTypeParameterMap = findSingleAbstractMethodOfInterface(typeContext);
            List<ParameterInfo> methodParams = methodTypeParameterMap.methodInfo.methodInspection.get().parameters;
            MethodTypeParameterMap concreteTypeMap = concreteType.findSingleAbstractMethodOfInterface(typeContext);
            List<ParameterInfo> concreteTypeAbstractParams = concreteTypeMap.methodInfo.methodInspection.get().parameters;

            if (methodParams.size() != concreteTypeAbstractParams.size()) {
                throw new UnsupportedOperationException("Have different param sizes for functional interface " +
                        detailedString() + " method " +
                        methodTypeParameterMap.methodInfo.fullyQualifiedName() + " and " +
                        concreteTypeMap.methodInfo.fullyQualifiedName());
            }
            for (int i = 0; i < methodParams.size(); i++) {
                ParameterizedType abstractTypeParameter = methodParams.get(i).parameterizedType;
                ParameterizedType concreteTypeParameter = concreteTypeMap.getConcreteTypeOfParameter(i);
                res.putAll(abstractTypeParameter.translateMap(concreteTypeParameter, typeContext));
            }
            // and now the return type
            ParameterizedType myReturnType = methodTypeParameterMap.getConcreteReturnType();
            ParameterizedType concreteReturnType = concreteTypeMap.getConcreteReturnType();
            res.putAll(myReturnType.translateMap(concreteReturnType, typeContext));
        } else {
            // it is possible that the concrete type has fewer type parameters than the formal one
            // e.g., when "new Stack<>" is to be matched with "Stack<String>"
            for (int i = 0; i < Math.min(parameters.size(), concreteType.parameters.size()); i++) {
                ParameterizedType abstractTypeParameter = parameters.get(i);
                ParameterizedType concreteTypeParameter = concreteType.parameters.get(i);
                res.putAll(abstractTypeParameter.translateMap(concreteTypeParameter, typeContext));
            }
        }

        return res;
    }

    public Set<TypeInfo> typeInfoSet() {
        Set<TypeInfo> set = new HashSet<>();
        if (isType()) set.add(typeInfo);
        for (ParameterizedType pt : parameters) {
            set.addAll(pt.typeInfoSet());
        }
        return set;
    }

    // semantics: can type be assigned to me? I should be equal or a super type of type

    /*
    The wildcard declaration of List<? extends Number> foo3 means that any of these are legal assignments:
        List<? extends Number> foo3 = new ArrayList<Number>();  // Number "extends" Number (in this context)
        List<? extends Number> foo3 = new ArrayList<Integer>(); // Integer extends Number
        List<? extends Number> foo3 = new ArrayList<Double>();  // Double extends Number
     The wildcard declaration of List<? super Integer> foo3 means that any of these are legal assignments:
        List<? super Integer> foo3 = new ArrayList<Integer>();  // Integer is a "superclass" of Integer (in this context)
        List<? super Integer> foo3 = new ArrayList<Number>();   // Number is a superclass of Integer
        List<? super Integer> foo3 = new ArrayList<Object>();   // Object is a superclass of Integer
     */
    public boolean isAssignableFrom(ParameterizedType type) {
        if (type == this) return true;
        if (typeInfo != null) {

            if ("java.lang.Object".equals(typeInfo.fullyQualifiedName)) return true;
            if ("java.util.function.Function".equals(typeInfo.fullyQualifiedName) ||
                    "java.util.function.BiFunction".equals(typeInfo.fullyQualifiedName) ||
                    "java.util.function.Supplier".equals(typeInfo.fullyQualifiedName)) {
                return parameters.get(parameters.size() - 1).isAssignableFrom(type);
            }
            if (type.typeInfo != null) {
                if (arrays != type.arrays) return false;
                if (typeInfo.equals(type.typeInfo)) return true;
                if (typeInfo.isPrimitive()) {
                    if (arrays == 0) {
                        if (isPrimitive()) {
                            return Primitives.PRIMITIVES.isAssignableFromTo(type, this);
                        }
                        return checkBoxing(type.typeInfo);
                    }
                    // TODO; for now: primitive array can only be assigned to its own type
                    return false;
                }
                if (isPrimitive()) {
                    // the other one is not a primitive
                    return arrays == 0 && type.checkBoxing(typeInfo);
                }

                for (ParameterizedType interfaceImplemented : type.typeInfo.typeInspection.get().interfacesImplemented) {
                    if (isAssignableFrom(interfaceImplemented)) return true;
                }
                if (type.typeInfo.typeInspection.get().parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT) {
                    if (isAssignableFrom(typeInfo.typeInspection.get().parentClass)) return true;
                }
            }
        }
        if (typeParameter != null) {
            // T extends Comparable<...> & Serializable
            List<ParameterizedType> typeBounds = typeParameter.typeParameterInspection.get().typeBounds;
            if (!typeBounds.isEmpty()) {
                if (wildCard == WildCard.EXTENDS) {
                    return typeBounds.stream().allMatch(this::isAssignableFrom);
                }
                if (wildCard == WildCard.SUPER) {
                    return typeBounds.stream().allMatch(tb -> tb.isAssignableFrom(this));
                }
                throw new UnsupportedOperationException("?");
            }
            if (type.isPrimitive()) {
                // int cannot be assigned to T, no matter what; neither can int[] to T[]
                return false;
            }
            return arrays <= type.arrays; // normally the wildcard is NONE, <T>, so anything goes
        }
        return wildCard == WildCard.UNBOUND; // <?> anything goes
    }

    private boolean checkBoxing(TypeInfo primitiveType) {
        String fqn = primitiveType.fullyQualifiedName;
        return typeInfo == Primitives.PRIMITIVES.longTypeInfo && "java.lang.Long".equals(fqn) ||
                typeInfo == Primitives.PRIMITIVES.intTypeInfo && "java.lang.Integer".equals(fqn) ||
                typeInfo == Primitives.PRIMITIVES.shortTypeInfo && "java.lang.Short".equals(fqn) ||
                typeInfo == Primitives.PRIMITIVES.byteTypeInfo && "java.lang.Byte".equals(fqn) ||
                typeInfo == Primitives.PRIMITIVES.charTypeInfo && "java.lang.Character".equals(fqn) ||
                typeInfo == Primitives.PRIMITIVES.booleanTypeInfo && "java.lang.Boolean".equals(fqn) ||
                typeInfo == Primitives.PRIMITIVES.floatTypeInfo && "java.lang.Float".equals(fqn) ||
                typeInfo == Primitives.PRIMITIVES.doubleTypeInfo && "java.lang.Double".equals(fqn);
    }

    public boolean isFunctionalInterface(TypeContext typeContext) {
        if (typeInfo == null || typeInfo.typeInspection.get().typeNature != TypeNature.INTERFACE) {
            return false;
        }
        return typeInfo.typeInspection.get().annotations.contains(typeContext.functionalInterface.get());
    }

    public boolean isUnboundParameterType() {
        return isTypeParameter() && wildCard == WildCard.NONE;
    }

    public MethodTypeParameterMap findSingleAbstractMethodOfInterface(TypeContext typeContext) {
        try {
            if (!isFunctionalInterface(typeContext)) return null;
            MethodInfo theMethod = typeInfo.typeInspection.get().methods.stream()
                    .filter(m -> !m.isStatic && !m.isDefaultImplementation).findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("Cannot find a single abstract method in the interface " + detailedString()));
            return new MethodTypeParameterMap(theMethod, initialTypeParameterMap());
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while looking for functional interface on type {}", detailedString());
            throw rte;
        }
    }

}
