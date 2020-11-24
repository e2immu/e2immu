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
import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.inspector.TypeInspector;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;

import static org.e2immu.analyser.model.TypeInspectionImpl.InspectionState.TRIGGER_BYTECODE_INSPECTION;

public class ParameterizedType {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterizedType.class);

    public static final ParameterizedType TYPE_OF_NO_FLOW = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType NULL_CONSTANT = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType RETURN_TYPE_OF_CONSTRUCTOR = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType NO_TYPE_GIVEN_IN_LAMBDA = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType WILDCARD_PARAMETERIZED_TYPE = new ParameterizedType(WildCard.UNBOUND);

    public enum WildCard {
        NONE, UNBOUND, SUPER, EXTENDS
    }

    @NotNull
    public static ParameterizedType from(TypeContext context, Type type) {
        return from(context, type, WildCard.NONE, false, null);
    }

    @NotNull
    public static ParameterizedType from(TypeContext context, Type type, boolean varargs, TypeInspector.DollarResolver dollarResolver) {
        return from(context, type, WildCard.NONE, varargs, dollarResolver);
    }

    private static ParameterizedType from(TypeContext context, Type type, WildCard wildCard, boolean varargs, TypeInspector.DollarResolver dollarResolver) {
        Type baseType = type;
        int arrays = 0;
        if (type.isArrayType()) {
            ArrayType arrayType = (ArrayType) type;
            baseType = arrayType.getComponentType();
            arrays = arrayType.getArrayLevel();
        }
        if (varargs) arrays++; // if we have a varargs type Object..., we store a parameterized type Object[]
        if (baseType.isPrimitiveType()) {
            return new ParameterizedType(context.getPrimitives().primitiveByName(baseType.asString()), arrays);
        }
        if (type instanceof WildcardType wildcardType) {
            if (wildcardType.getExtendedType().isPresent()) {
                // ? extends T
                return from(context, wildcardType.getExtendedType().get(), WildCard.EXTENDS, false, dollarResolver);
            }
            if (wildcardType.getSuperType().isPresent()) {
                // ? super T
                return from(context, wildcardType.getSuperType().get(), WildCard.SUPER, false, dollarResolver);
            }
            return WILDCARD_PARAMETERIZED_TYPE; // <?>
        }
        if ("void".equals(baseType.asString())) return context.getPrimitives().voidParameterizedType;

        String name;
        List<ParameterizedType> parameters = new ArrayList<>();
        if (baseType instanceof ClassOrInterfaceType cit) {
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
                    TypeInspection scopeInspection = context.getTypeInspection(scopePt.typeInfo);
                    if (scopeInspection != null) {
                        Optional<TypeInfo> subType = scopeInspection.subTypes().stream().filter(st -> st.simpleName.equals(name)).findFirst();
                        if (subType.isPresent()) {
                            return parameters.isEmpty() ? new ParameterizedType(subType.get(), arrays) : new ParameterizedType(subType.get(), parameters);
                        }
                        Optional<FieldInfo> field = scopeInspection.fields().stream().filter(f -> f.name.equals(name)).findFirst();
                        if (field.isPresent()) return field.get().type;
                        throw new UnsupportedOperationException("Cannot find " + name + " in " + scopePt);
                    }
                    // we're going to assume that we're creating a subtype
                    String subTypeFqn = scopePt.typeInfo.fullyQualifiedName + "." + name;
                    TypeInfo subType = context.typeMapBuilder.getOrCreate(subTypeFqn, TRIGGER_BYTECODE_INSPECTION);
                    return parameters.isEmpty() ? new ParameterizedType(subType, arrays) : new ParameterizedType(subType, parameters);
                }
            }// else {
            // class or interface type, but completely without scope? we should look in our own hierarchy (this scope)
            // could be a subtype of one of the interfaces (here, we're in an implementation of Expression, and InScopeType is a subtype of Expression)
            // TODO
            //}
        } else {
            name = baseType.asString();
        }
        NamedType namedType = dollarResolver == null ? null : dollarResolver.apply(name);
        if (namedType == null) namedType = context.get(name, false);
        if (namedType instanceof TypeInfo typeInfo) {
            if (parameters.isEmpty()) {
                return new ParameterizedType(typeInfo, arrays);
            }
            // Set<T>... == Set<T>[]
            return new ParameterizedType(typeInfo, arrays, WildCard.NONE, parameters);
        }
        if (namedType instanceof TypeParameter typeParameter) {
            if (!parameters.isEmpty()) {
                throw new UnsupportedOperationException("??");
            }
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

    public Set<ParameterizedType> components(boolean includeMySelf) {
        Set<ParameterizedType> result = new HashSet<>();
        // T[], int[][], ...
        if (arrays > 0) result.add(new ParameterizedType(typeInfo, 0, wildCard, List.of(), typeParameter));
        if (!parameters.isEmpty()) {
            parameters.forEach(pt -> result.addAll(pt.components(true)));
        }
        if (includeMySelf) result.add(this);
        return result;
    }

    // from one type context into another one
    public ParameterizedType copy(TypeContext localTypeContext) {
        TypeInfo newTypeInfo;
        if (typeInfo == null || Primitives.isPrimitiveExcludingVoid(typeInfo)) {
            newTypeInfo = typeInfo;
        } else {
            newTypeInfo = Objects.requireNonNull(localTypeContext.typeMapBuilder.get(typeInfo.fullyQualifiedName),
                    "Cannot find " + typeInfo.fullyQualifiedName + " in typeStore");
        }
        List<ParameterizedType> newParameters = parameters.stream().map(pt -> pt.copy(localTypeContext)).collect(Collectors.toList());
        TypeParameter newTypeParameter = typeParameter == null ? null : (TypeParameter) localTypeContext.get(typeParameter.getName(), true);
        return new ParameterizedType(newTypeInfo, arrays, wildCard, newParameters, newTypeParameter);
    }

    public ParameterizedType copyWithOneFewerArrays() {
        if (arrays == 0) throw new UnsupportedOperationException();
        return new ParameterizedType(this.typeInfo, arrays - 1, wildCard, parameters, typeParameter);
    }

    public String print(PrintMode printMode) {
        return stream(printMode.forDebug(), false, false, false);
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
     * @param varArgs property of the type
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
                boolean isType = typeParameter.getOwner().isLeft();
                sb.append(isType ? "T" : "M").append(typeParameter.getIndex());
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterizedType that = (ParameterizedType) o;
        return arrays == that.arrays &&
                Objects.equals(typeInfo, that.typeInfo) &&
                parameters.equals(that.parameters) &&
                Objects.equals(typeParameter, that.typeParameter) &&
                wildCard == that.wildCard;
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeInfo, parameters, typeParameter, arrays, wildCard);
    }

    public static boolean notEqualsTypeParametersOnlyIndex(ParameterizedType pt1, ParameterizedType pt2) {
        if (pt1.typeInfo == null && pt2.typeInfo != null) return true;
        if (pt1.typeInfo != null && pt2.typeInfo == null) return true;
        if (pt1.typeInfo != null && !pt1.typeInfo.equals(pt2.typeInfo)) return true;
        if (pt1.parameters.size() != pt2.parameters.size()) return true;
        int i = 0;
        for (ParameterizedType parameter1 : pt1.parameters) {
            ParameterizedType parameter2 = pt2.parameters.get(i++);
            if (notEqualsTypeParametersOnlyIndex(parameter1, parameter2)) return true;
        }
        if (pt1.typeParameter == null && pt2.typeParameter != null) return true;
        if (pt1.typeParameter != null && pt2.typeParameter == null) return true;
        if (pt1.typeParameter == null) return false;
        return pt1.typeParameter.getIndex() != pt2.typeParameter.getIndex();
    }

    public boolean allowsForOperators() {
        if (Primitives.isVoid(this)) return false;
        if (typeInfo == null) return false;
        return Primitives.isPrimitiveExcludingVoid(typeInfo)
                || Primitives.isBoxedExcludingVoid(typeInfo)
                || Primitives.isJavaLangString(typeInfo);
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

    public Map<NamedType, ParameterizedType> translateMap(InspectionProvider inspectionProvider, ParameterizedType concreteType) {
        if (parameters.isEmpty()) {
            if (isTypeParameter())
                return Map.of(this.typeParameter, concreteType);
            return Map.of();
        }
        Map<NamedType, ParameterizedType> res = new HashMap<>();
        boolean iAmFunctionalInterface = isFunctionalInterface();
        boolean concreteTypeIsFunctionalInterface = concreteType.isFunctionalInterface();

        if (iAmFunctionalInterface && concreteTypeIsFunctionalInterface) {
            MethodTypeParameterMap methodTypeParameterMap = findSingleAbstractMethodOfInterface(inspectionProvider);
            List<ParameterInfo> methodParams = methodTypeParameterMap.methodInspection.getParameters();
            MethodTypeParameterMap concreteTypeMap = concreteType.findSingleAbstractMethodOfInterface(inspectionProvider);
            List<ParameterInfo> concreteTypeAbstractParams = concreteTypeMap.methodInspection.getParameters();

            if (methodParams.size() != concreteTypeAbstractParams.size()) {
                throw new UnsupportedOperationException("Have different param sizes for functional interface " +
                        detailedString() + " method " +
                        methodTypeParameterMap.methodInspection.getFullyQualifiedName() + " and " +
                        concreteTypeMap.methodInspection.getFullyQualifiedName());
            }
            for (int i = 0; i < methodParams.size(); i++) {
                ParameterizedType abstractTypeParameter = methodParams.get(i).parameterizedType;
                ParameterizedType concreteTypeParameter = concreteTypeMap.getConcreteTypeOfParameter(i);
                res.putAll(abstractTypeParameter.translateMap(inspectionProvider, concreteTypeParameter));
            }
            // and now the return type
            ParameterizedType myReturnType = methodTypeParameterMap.getConcreteReturnType();
            ParameterizedType concreteReturnType = concreteTypeMap.getConcreteReturnType();
            res.putAll(myReturnType.translateMap(inspectionProvider, concreteReturnType));
        } else {
            // it is possible that the concrete type has fewer type parameters than the formal one
            // e.g., when "new Stack<>" is to be matched with "Stack<String>"
            for (int i = 0; i < Math.min(parameters.size(), concreteType.parameters.size()); i++) {
                ParameterizedType abstractTypeParameter = parameters.get(i);
                ParameterizedType concreteTypeParameter = concreteType.parameters.get(i);
                res.putAll(abstractTypeParameter.translateMap(inspectionProvider, concreteTypeParameter));
            }
        }

        return res;
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

    public boolean isAssignableFrom(InspectionProvider inspectionProvider, ParameterizedType type) {
        return numericIsAssignableFrom(inspectionProvider, type) != NOT_ASSIGNABLE;
    }

    private static final IntBinaryOperator REDUCER = (a, b) -> a == NOT_ASSIGNABLE || b == NOT_ASSIGNABLE ? NOT_ASSIGNABLE : a + b;

    private static final int SAME_UNDERLYING_TYPE = 1;
    private static final int BOXING_TO_PRIMITIVE = 1;
    private static final int BOXING_FROM_PRIMITIVE = 1;
    private static final int ARRAY_DIFFERENCE_TYPE_PARAMS = 10;
    private static final int IN_HIERARCHY = 100;
    private static final int UNBOUND_WILDCARD = 1000;

    public int numericIsAssignableFrom(InspectionProvider inspectionProvider, ParameterizedType type) {
        return numericIsAssignableFrom(inspectionProvider, type, false);
    }

    private int numericIsAssignableFrom(InspectionProvider inspectionProvider, ParameterizedType type, boolean ignoreArrays) {
        Objects.requireNonNull(type);
        if (type == this || equals(type)) return 0;
        if (type == ParameterizedType.NULL_CONSTANT) {
            if (Primitives.isPrimitiveExcludingVoid(this)) return NOT_ASSIGNABLE;
            return 1;
        }
        if (typeInfo != null) {
            if ("java.lang.Object".equals(typeInfo.fullyQualifiedName)) return IN_HIERARCHY;
            if (type.typeInfo != null) {
                if (!ignoreArrays && arrays != type.arrays) return NOT_ASSIGNABLE;
                if (typeInfo.equals(type.typeInfo)) {
                    return SAME_UNDERLYING_TYPE;
                }
                if (Primitives.isPrimitiveExcludingVoid(type)) {
                    if (arrays == 0) {
                        if (Primitives.isPrimitiveExcludingVoid(this)) {
                            return inspectionProvider.getPrimitives().isAssignableFromTo(type, this);
                        }
                        return checkBoxing(inspectionProvider.getPrimitives(), type.typeInfo) ? BOXING_FROM_PRIMITIVE : NOT_ASSIGNABLE;
                    }
                    // TODO; for now: primitive array can only be assigned to its own type
                    return NOT_ASSIGNABLE;
                }
                if (Primitives.isPrimitiveExcludingVoid(this)) {
                    // the other one is not a primitive
                    return arrays == 0 && type.checkBoxing(inspectionProvider.getPrimitives(), typeInfo) ? BOXING_TO_PRIMITIVE : NOT_ASSIGNABLE;
                }

                TypeInspection typeInspection = inspectionProvider.getTypeInspection(type.typeInfo);
                for (ParameterizedType interfaceImplemented : typeInspection.interfacesImplemented()) {
                    int scoreInterface = numericIsAssignableFrom(inspectionProvider, interfaceImplemented, true);
                    if (scoreInterface != NOT_ASSIGNABLE) return IN_HIERARCHY + scoreInterface;
                }
                ParameterizedType parentClass = typeInspection.parentClass();
                if (!Primitives.isJavaLangObject(parentClass)) {
                    int scoreParent = numericIsAssignableFrom(inspectionProvider, parentClass, true);
                    if (scoreParent != NOT_ASSIGNABLE) return IN_HIERARCHY + scoreParent;
                }
            }
        }
        if (typeParameter != null) {
            // T extends Comparable<...> & Serializable
            try {
                List<ParameterizedType> typeBounds = typeParameter.getTypeBounds();
                if (!typeBounds.isEmpty()) {
                    if (wildCard == WildCard.EXTENDS) {
                        return typeBounds.stream().mapToInt(pt -> numericIsAssignableFrom(inspectionProvider, pt)).reduce(IN_HIERARCHY, REDUCER);
                    }
                    if (wildCard == WildCard.SUPER) {
                        return typeBounds.stream().mapToInt(tb -> tb.numericIsAssignableFrom(inspectionProvider, this)).reduce(IN_HIERARCHY, REDUCER);
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

    private boolean checkBoxing(Primitives primitives, TypeInfo primitiveType) {
        TypeInfo boxed = primitiveType.asParameterizedType().toBoxed(primitives);
        return boxed == typeInfo;
    }

    public boolean isFunctionalInterface() {
        if (typeInfo == null) return false;
        return typeInfo.typeInspection.get().isFunctionalInterface();
    }

    public boolean isFunctionalInterface(InspectionProvider inspectionProvider) {
        if (typeInfo == null) return false;
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        return typeInspection.isFunctionalInterface();
    }

    public boolean isUnboundParameterType() {
        return isTypeParameter() && wildCard == WildCard.NONE;
    }

    public MethodTypeParameterMap findSingleAbstractMethodOfInterface(InspectionProvider inspectionProvider) {
        return findSingleAbstractMethodOfInterface(inspectionProvider, true);
    }

    private MethodTypeParameterMap findSingleAbstractMethodOfInterface(InspectionProvider inspectionProvider, boolean complain) {
        if (!isFunctionalInterface(inspectionProvider)) return null;
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        Optional<MethodInspection> theMethod = typeInspection.methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .map(inspectionProvider::getMethodInspection)
                .filter(m -> !m.isStatic() && !m.isDefault()).findFirst();
        if (theMethod.isPresent()) {
            return new MethodTypeParameterMap(theMethod.get(), initialTypeParameterMap());
        }
        for (ParameterizedType extension : typeInspection.interfacesImplemented()) {
            MethodTypeParameterMap ofExtension = extension.findSingleAbstractMethodOfInterface(inspectionProvider, false);
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

    public int getProperty(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        TypeInfo bestType = bestTypeInfo();
        if (bestType != null) {
            return analysisProvider.getTypeAnalysis(bestType).getProperty(variableProperty);
        }
        return variableProperty.falseValue;
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

    /**
     * return the best common type
     *
     * @param other the other type
     * @return the common type
     */
    public ParameterizedType commonType(InspectionProvider inspectionProvider, ParameterizedType other) {
        if (other == null) return null;
        if (equals(other)) return this;
        TypeInfo bestType = bestTypeInfo();
        TypeInfo otherBestType = other.bestTypeInfo();
        boolean isPrimitive = Primitives.isPrimitiveExcludingVoid(this) || bestType != null && Primitives.isBoxedExcludingVoid(bestType);
        boolean otherIsPrimitive = Primitives.isPrimitiveExcludingVoid(other) || otherBestType != null && Primitives.isBoxedExcludingVoid(otherBestType);
        if (isPrimitive && otherIsPrimitive) {
            return inspectionProvider.getPrimitives().widestType(this, other);
        }
        if (isPrimitive || otherIsPrimitive) return null; // no common type
        if (bestType == null || otherBestType == null) return null;
        if (isAssignableFrom(inspectionProvider, other)) {
            return this;
        }
        if (other.isAssignableFrom(inspectionProvider, this)) {
            return other;
        }
        return null;
    }

    public Boolean isImplicitlyOrAtLeastEventuallyE2Immutable(AnalysisProvider analysisProvider) {
        if (arrays > 0) return false;
        if (isUnboundParameterType()) return true;
        Boolean immu = isAtLeastEventuallyE2Immutable(analysisProvider);
        if (immu == Boolean.TRUE) return true;
        TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(bestTypeInfo());
        if (typeAnalysis.getImplicitlyImmutableDataTypes() == null) {
            // not yet defined
            return null;
        }
        boolean implicit = typeAnalysis.getImplicitlyImmutableDataTypes().contains(this);
        if (implicit) return true;
        return immu;
    }

    public Boolean isAtLeastEventuallyE2Immutable(AnalysisProvider analysisProvider) {
        TypeInfo bestType = bestTypeInfo();
        if (bestType == null) return false;
        int immutable = analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
        if (immutable == Level.DELAY) return null;
        return MultiLevel.isAtLeastEventuallyE2Immutable(immutable);
    }

    public TypeInfo toBoxed(Primitives primitives) {
        return primitives.boxed(typeInfo);
    }

    public ParameterizedType mostSpecific(InspectionProvider inspectionProvider, ParameterizedType other) {
        if (isType() && Primitives.isVoid(typeInfo) || other.isType() && Primitives.isVoid(other.typeInfo)) {
            return inspectionProvider.getPrimitives().voidParameterizedType;
        }
        if (isAssignableFrom(inspectionProvider, other)) {
            return other;
        }
        return this;
    }

    public UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        return UpgradableBooleanMap.of(
                parameters.stream().flatMap(pt -> pt.typesReferenced(explicit).stream()).collect(UpgradableBooleanMap.collector()),
                isType() && !Primitives.isPrimitiveExcludingVoid(typeInfo) ?
                        UpgradableBooleanMap.of(typeInfo, explicit) : UpgradableBooleanMap.of());
    }

    public boolean equalsErased(ParameterizedType other) {
        TypeInfo best = bestTypeInfo();
        TypeInfo otherBest = other.bestTypeInfo();
        if (best == null) return otherBest == null;
        return best.equals(otherBest) && arrays == other.arrays;
    }

}
