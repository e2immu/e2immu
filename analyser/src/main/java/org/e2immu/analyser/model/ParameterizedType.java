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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.PrimitiveValue;
import org.e2immu.analyser.model.value.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;

public class ParameterizedType {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterizedType.class);

    public static final ParameterizedType NULL_CONSTANT = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType RETURN_TYPE_OF_CONSTRUCTOR = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType NO_TYPE_GIVEN_IN_LAMBDA = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType IMPLICITLY_JAVA_LANG_OBJECT = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType WILDCARD_PARAMETERIZED_TYPE = new ParameterizedType(WildCard.UNBOUND);

    public enum WildCard {
        NONE, UNBOUND, SUPER, EXTENDS;
    }

    @NotNull
    public static ParameterizedType from(TypeContext context, Type type) {
        return from(context, type, WildCard.NONE, false);
    }

    @NotNull
    public static ParameterizedType from(TypeContext context, Type type, boolean varargs) {
        return from(context, type, WildCard.NONE, varargs);
    }

    private static ParameterizedType from(TypeContext context, Type type, WildCard wildCard, boolean varargs) {
        Type baseType = type;
        int arrays = 0;
        if (type.isArrayType()) {
            ArrayType arrayType = (ArrayType) type;
            baseType = arrayType.getComponentType();
            arrays = arrayType.getArrayLevel();
        }
        if (varargs) arrays++; // if we have a varargs type Object..., we store a parameterized type Object[]
        if (baseType.isPrimitiveType()) {
            return new ParameterizedType(Primitives.PRIMITIVES.primitiveByName(baseType.asString()), arrays);
        }
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            if (wildcardType.getExtendedType().isPresent()) {
                // ? extends T
                return from(context, wildcardType.getExtendedType().get(), WildCard.EXTENDS, false);
            }
            if (wildcardType.getSuperType().isPresent()) {
                // ? super T
                return from(context, wildcardType.getSuperType().get(), WildCard.SUPER, false);
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
                if (scopePt.typeInfo != null) {
                    if (scopePt.typeInfo.typeInspection.isSetDoNotTriggerRunnable()) {
                        Optional<TypeInfo> subType = scopePt.typeInfo.typeInspection.get().subTypes.stream().filter(st -> st.simpleName.equals(name)).findFirst();
                        if (subType.isPresent()) {
                            return parameters.isEmpty() ? new ParameterizedType(subType.get(), arrays) : new ParameterizedType(subType.get(), parameters);
                        }
                        Optional<FieldInfo> field = scopePt.typeInfo.typeInspection.get().fields.stream().filter(f -> f.name.equals(name)).findFirst();
                        if (field.isPresent()) return field.get().type;
                        throw new UnsupportedOperationException("Cannot find " + name + " in " + scopePt);
                    }
                    // we're going to assume that we're creating a subtype
                    String subTypeFqn = scopePt.typeInfo.fullyQualifiedName + "." + name;
                    TypeInfo subType = context.typeStore.getOrCreate(subTypeFqn);
                    return parameters.isEmpty() ? new ParameterizedType(subType, arrays) : new ParameterizedType(subType, parameters);
                }
            } else {
                // class or interface type, but completely without scope? we should look in our own hierarchy (this scope)
                // could be a subtype of one of the interfaces (here, we're in an implementation of Expression, and InScopeType is a subtype of Expression)
                LOGGER.debug("?");
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
            // Set<T>... == Set<T>[]
            return new ParameterizedType(typeInfo, arrays, WildCard.NONE, parameters);
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

    private ParameterizedType(TypeInfo typeInfo, int arrays, WildCard wildCard, List<ParameterizedType> typeParameters, TypeParameter typeParameter) {
        this.typeInfo = typeInfo;
        this.arrays = arrays;
        this.wildCard = wildCard;
        this.parameters = typeParameters;
        this.typeParameter = typeParameter;
    }

    // from one type context into another one
    public ParameterizedType copy(TypeContext localTypeContext) {
        TypeInfo newTypeInfo = typeInfo == null ? null : localTypeContext.typeStore.get(typeInfo.fullyQualifiedName);
        List<ParameterizedType> newParameters = parameters.stream().map(pt -> pt.copy(localTypeContext)).collect(Collectors.toList());
        TypeParameter newTypeParameter = typeParameter == null ? null : (TypeParameter) localTypeContext.get(typeParameter.name, true);
        return new ParameterizedType(newTypeInfo, arrays, wildCard, newParameters, newTypeParameter);
    }

    public ParameterizedType copyWithOneFewerArrays() {
        if (arrays == 0) throw new UnsupportedOperationException();
        return new ParameterizedType(this.typeInfo, arrays - 1, wildCard, parameters, typeParameter);
    }

    @Override
    public String toString() {
        return (isType() ? "Type " : isTypeParameter() ? "Type param " : "") +
                stream(true, false, false, false);
    }

    public String stream() {
        return stream(false, false, false, false);
    }

    public String stream(boolean varargs) {
        return stream(false, false, varargs, false);
    }

    /**
     * Stream for sending the qualified name to the KV store
     *
     * @param varArgs
     * @return the type as a fully qualified name, with type parameters according to the format
     * Tn or Mn, with n the index, and T for type, M for method
     */
    public String distinguishingStream(boolean varArgs) {
        return stream(true, true, varArgs, false);
    }

    public String detailedString() {
        return stream(true, false, false, false);
    }

    public String streamWithoutArrays() {
        return stream(false, false, false, true);
    }

    private String stream(boolean fullyQualified, boolean numericTypeParameters, boolean varargs, boolean withoutArrays) {
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
                        .map(pt -> pt.stream(fullyQualified, numericTypeParameters, false, false))
                        .collect(Collectors.joining(", ")));
                sb.append(">");
            }
        }
        if (!withoutArrays) {
            if (varargs) {
                if (arrays == 0) {
                    throw new UnsupportedOperationException("Varargs parameterized types must have arrays>0!");
                }
                sb.append("[]".repeat(arrays - 1)).append("...");
            } else {
                sb.append("[]".repeat(arrays));
            }
        }
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

    public static boolean equalsTypeParametersOnlyIndex(ParameterizedType pt1, ParameterizedType pt2) {
        if (pt1.typeInfo == null && pt2.typeInfo != null) return false;
        if (pt1.typeInfo != null && pt2.typeInfo == null) return false;
        if (pt1.typeInfo != null && !pt1.typeInfo.equals(pt2.typeInfo)) return false;
        if (pt1.parameters.size() != pt2.parameters.size()) return false;
        int i = 0;
        for (ParameterizedType parameter1 : pt1.parameters) {
            ParameterizedType parameter2 = pt2.parameters.get(i++);
            if (!equalsTypeParametersOnlyIndex(parameter1, parameter2)) return false;
        }
        if (pt1.typeParameter == null && pt2.typeParameter != null) return false;
        if (pt1.typeParameter != null && pt2.typeParameter == null) return false;
        if (pt1.typeParameter == null) return true;
        return pt1.typeParameter.index == pt2.typeParameter.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeInfo, parameters, typeParameter);
    }

    public boolean isPrimitive() {
        return typeInfo != null && typeInfo.isPrimitive();
    }

    public boolean isVoid() {
        return typeInfo == Primitives.PRIMITIVES.voidTypeInfo || typeInfo == Primitives.PRIMITIVES.boxedVoidTypeInfo;
    }

    public boolean allowsForOperators() {
        if (isVoid()) return false;
        if (typeInfo == null) return false;
        return Primitives.PRIMITIVES.primitives.contains(typeInfo)
                || Primitives.PRIMITIVES.boxed.contains(typeInfo)
                || typeInfo == Primitives.PRIMITIVES.stringTypeInfo;
    }

    // ******************************************************************************************************

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
        boolean iAmFunctionalInterface = isFunctionalInterface();
        boolean concreteTypeIsFunctionalInterface = concreteType.isFunctionalInterface();

        if (iAmFunctionalInterface && concreteTypeIsFunctionalInterface) {
            MethodTypeParameterMap methodTypeParameterMap = findSingleAbstractMethodOfInterface();
            List<ParameterInfo> methodParams = methodTypeParameterMap.methodInfo.methodInspection.get().parameters;
            MethodTypeParameterMap concreteTypeMap = concreteType.findSingleAbstractMethodOfInterface();
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
    public static int NOT_ASSIGNABLE = -1;

    public boolean isAssignableFrom(ParameterizedType type) {
        return numericIsAssignableFrom(type) != NOT_ASSIGNABLE;
    }

    private static final IntBinaryOperator REDUCER = (a, b) -> a == NOT_ASSIGNABLE || b == NOT_ASSIGNABLE ? NOT_ASSIGNABLE : a + b;

    private static final int SAME_UNDERLYING_TYPE = 1;
    private static final int BOXING_TO_PRIMITIVE = 1;
    private static final int BOXING_FROM_PRIMITIVE = 1;
    private static final int ARRAY_DIFFERENCE_TYPE_PARAMS = 10;
    private static final int IN_HIERARCHY = 100;
    private static final int UNBOUND_WILDCARD = 1000;

    public int numericIsAssignableFrom(ParameterizedType type) {
        Objects.requireNonNull(type);
        if (type == this || equals(type)) return 0;
        if (type == ParameterizedType.NULL_CONSTANT) {
            if (isPrimitive()) return NOT_ASSIGNABLE;
            return 1;
        }
        if (typeInfo != null) {
            if ("java.lang.Object".equals(typeInfo.fullyQualifiedName)) return IN_HIERARCHY;
            if (type.typeInfo != null) {
                if (arrays != type.arrays) return NOT_ASSIGNABLE;
                if (typeInfo.equals(type.typeInfo)) {
                    return SAME_UNDERLYING_TYPE;
                }
                if (type.isPrimitive()) {
                    if (arrays == 0) {
                        if (isPrimitive()) {
                            return Primitives.PRIMITIVES.isAssignableFromTo(type, this);
                        }
                        return checkBoxing(type.typeInfo) ? BOXING_FROM_PRIMITIVE : NOT_ASSIGNABLE;
                    }
                    // TODO; for now: primitive array can only be assigned to its own type
                    return NOT_ASSIGNABLE;
                }
                if (isPrimitive()) {
                    // the other one is not a primitive
                    return arrays == 0 && type.checkBoxing(typeInfo) ? BOXING_TO_PRIMITIVE : NOT_ASSIGNABLE;
                }

                for (ParameterizedType interfaceImplemented : type.typeInfo.typeInspection.get().interfacesImplemented) {
                    int scoreInterface = numericIsAssignableFrom(interfaceImplemented);
                    if (scoreInterface != NOT_ASSIGNABLE) return IN_HIERARCHY + scoreInterface;
                }
                ParameterizedType parentClass = type.typeInfo.typeInspection.get().parentClass;
                if (parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT) {
                    int scoreParent = numericIsAssignableFrom(parentClass);
                    if (scoreParent != NOT_ASSIGNABLE) return IN_HIERARCHY + scoreParent;
                }
            }
        }
        if (typeParameter != null) {
            // T extends Comparable<...> & Serializable
            try {
                //if(typeParameter.owner.isLeft()) typeParameter.owner.getLeft().typeInspection.get();
                //else typeParameter.owner.getRight().typeInfo.typeInspection.get();

                List<ParameterizedType> typeBounds = typeParameter.typeParameterInspection.get().typeBounds;
                if (!typeBounds.isEmpty()) {
                    if (wildCard == WildCard.EXTENDS) {
                        return typeBounds.stream().mapToInt(this::numericIsAssignableFrom).reduce(IN_HIERARCHY, REDUCER);
                    }
                    if (wildCard == WildCard.SUPER) {
                        return typeBounds.stream().mapToInt(tb -> tb.numericIsAssignableFrom(this)).reduce(IN_HIERARCHY, REDUCER);
                    }
                    throw new UnsupportedOperationException("?");
                }
                return arrays <= type.arrays ? ARRAY_DIFFERENCE_TYPE_PARAMS : NOT_ASSIGNABLE; // normally the wildcard is NONE, <T>, so anything goes
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught exception examining type bounds of {}", typeParameter.toString());
                throw rte;
            }
        }
        return wildCard == WildCard.UNBOUND ? UNBOUND_WILDCARD : NOT_ASSIGNABLE; // <?> anything goes
    }

    private boolean checkBoxing(TypeInfo primitiveType) {
        return primitiveType == Primitives.PRIMITIVES.longTypeInfo && typeInfo == Primitives.PRIMITIVES.boxedLongTypeInfo ||
                primitiveType == Primitives.PRIMITIVES.intTypeInfo && typeInfo == Primitives.PRIMITIVES.integerTypeInfo ||
                primitiveType == Primitives.PRIMITIVES.shortTypeInfo && typeInfo == Primitives.PRIMITIVES.boxedShortTypeInfo ||
                primitiveType == Primitives.PRIMITIVES.byteTypeInfo && typeInfo == Primitives.PRIMITIVES.boxedByteTypeInfo ||
                primitiveType == Primitives.PRIMITIVES.charTypeInfo && typeInfo == Primitives.PRIMITIVES.characterTypeInfo ||
                primitiveType == Primitives.PRIMITIVES.booleanTypeInfo && typeInfo == Primitives.PRIMITIVES.boxedBooleanTypeInfo ||
                primitiveType == Primitives.PRIMITIVES.floatTypeInfo && typeInfo == Primitives.PRIMITIVES.boxedFloatTypeInfo ||
                primitiveType == Primitives.PRIMITIVES.doubleTypeInfo && typeInfo == Primitives.PRIMITIVES.boxedDoubleTypeInfo;
    }

    public boolean isFunctionalInterface() {
        if (typeInfo == null) return false;
        return typeInfo.isFunctionalInterface();
    }

    public boolean isUnboundParameterType() {
        return isTypeParameter() && wildCard == WildCard.NONE;
    }

    public MethodTypeParameterMap findSingleAbstractMethodOfInterface() {
        return findSingleAbstractMethodOfInterface(true);
    }

    private MethodTypeParameterMap findSingleAbstractMethodOfInterface(boolean complain) {
        if (!isFunctionalInterface()) return null;
        Optional<MethodInfo> theMethod = typeInfo.typeInspection.get().methods.stream()
                .filter(m -> !m.isStatic && !m.isDefaultImplementation).findFirst();
        if (theMethod.isPresent()) return new MethodTypeParameterMap(theMethod.get(), initialTypeParameterMap());
        for (ParameterizedType extension : typeInfo.typeInspection.get().interfacesImplemented) {
            MethodTypeParameterMap ofExtension = extension.findSingleAbstractMethodOfInterface(false);
            if (ofExtension != null) {
                return ofExtension;
            }
        }
        if (complain) {
            throw new UnsupportedOperationException("Cannot find a single abstract method in the interface " + detailedString());
        }
        return null;
    }

    public boolean betterDefinedThan(ParameterizedType v) {
        return (typeParameter != null || typeInfo != null) && v.typeParameter == null && v.typeInfo == null;
    }


    /*
     * Pair<Integer, String> pair = new Pair<>(); expression = pair.k
     * The scope of the expression is 'pair', which has a concrete return type of 'Pair<Integer, String>'.
     * The field 'k' has an abstract type K, which can be filled to get a concrete return type Integer
     */

    public ParameterizedType fillTypeParameters(ParameterizedType concreteType) {
        Map<NamedType, ParameterizedType> typeParameterMap = concreteType.initialTypeParameterMap();
        return MethodTypeParameterMap.apply(typeParameterMap, this);
    }

    public int getProperty(VariableProperty variableProperty) {
        TypeInfo bestType = bestTypeInfo();
        if (bestType != null) {
            return bestType.typeAnalysis.get().getProperty(variableProperty);
        }
        if (variableProperty == VariableProperty.MODIFIED) return isUnboundParameterType() ? Level.FALSE : Level.TRUE;
        return Level.FALSE;
    }

    public TypeInfo bestTypeInfo() {
        if (typeInfo != null) return typeInfo;
        if (typeParameter != null && wildCard == WildCard.EXTENDS && parameters.size() == 1) {
            return parameters.get(0).bestTypeInfo();
        }
        return null;
    }

    public boolean isRecordType() {
        if (typeInfo != null) {
            return typeInfo.isRecord();
        }
        if (typeParameter != null && wildCard == WildCard.EXTENDS && parameters.size() == 1) {
            return parameters.get(0).isRecordType();
        }
        return false;
    }

    public Value defaultValue() {
        if (isPrimitive()) {
            if (typeInfo == Primitives.PRIMITIVES.booleanTypeInfo) return BoolValue.FALSE;
            if (typeInfo == Primitives.PRIMITIVES.intTypeInfo) return IntValue.ZERO_VALUE;
            if (typeInfo == Primitives.PRIMITIVES.longTypeInfo) return new LongValue(0L);
            if (typeInfo == Primitives.PRIMITIVES.charTypeInfo) return new CharValue('\0');
            throw new UnsupportedOperationException();
        }
        return NullValue.NULL_VALUE;
    }

    public boolean isBoolean() {
        return typeInfo == Primitives.PRIMITIVES.boxedBooleanTypeInfo || Primitives.PRIMITIVES.booleanTypeInfo == typeInfo;
    }

    public boolean hasSize() {
        TypeInfo bestType = bestTypeInfo();
        return bestType != null && bestType.hasSize();
    }

    public boolean isDiscrete() {
        return typeInfo == Primitives.PRIMITIVES.intTypeInfo || typeInfo == Primitives.PRIMITIVES.longTypeInfo ||
                typeInfo == Primitives.PRIMITIVES.shortTypeInfo || typeInfo == Primitives.PRIMITIVES.byteTypeInfo ||
                typeInfo == Primitives.PRIMITIVES.integerTypeInfo || typeInfo == Primitives.PRIMITIVES.boxedLongTypeInfo ||
                typeInfo == Primitives.PRIMITIVES.boxedShortTypeInfo || typeInfo == Primitives.PRIMITIVES.boxedByteTypeInfo;
    }
}
