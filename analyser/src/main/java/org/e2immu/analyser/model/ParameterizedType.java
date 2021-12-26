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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.inspector.MethodTypeParameterMap;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParameterizedType {
    public static final ParameterizedType NULL_CONSTANT = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType RETURN_TYPE_OF_CONSTRUCTOR = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType NO_TYPE_GIVEN_IN_LAMBDA = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType WILDCARD_PARAMETERIZED_TYPE = new ParameterizedType(WildCard.UNBOUND);

    public enum WildCard {
        NONE, UNBOUND, SUPER, EXTENDS
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
        this.parameters = List.copyOf(Objects.requireNonNull(parameters));
        this.typeParameter = null;
        this.arrays = 0;
        this.wildCard = WildCard.NONE;
    }

    // all-in method used by the ParameterizedTypeFactory in byte code inspector
    public ParameterizedType(TypeInfo typeInfo, int arrays, WildCard wildCard, List<ParameterizedType> typeParameters) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.arrays = arrays;
        this.wildCard = wildCard;
        this.parameters = List.copyOf(typeParameters);
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

    public ParameterizedType copyWithoutArrays() {
        if (arrays == 0) throw new UnsupportedOperationException();
        return new ParameterizedType(this.typeInfo, 0, wildCard, parameters, typeParameter);
    }

    public OutputBuilder output(Qualification qualification) {
        return ParameterizedTypePrinter.print(InspectionProvider.DEFAULT, qualification, this,
                false, Diamond.SHOW_ALL, false);
    }

    public OutputBuilder output(Qualification qualification, boolean varArgs, Diamond diamond) {
        return ParameterizedTypePrinter.print(InspectionProvider.DEFAULT, qualification, this, varArgs, diamond,
                false);
    }

    @Override
    public String toString() {
        return (isType() ? "Type " : isTypeParameter() ? "Type param " : "") + detailedString();
    }

    /*
    used in MethodItem (XML)
     */
    public String print() {
        return ParameterizedTypePrinter.print(InspectionProvider.DEFAULT, Qualification.FULLY_QUALIFIED_NAME,
                this, false, Diamond.SHOW_ALL, false).toString();
    }

    /*
    Used in NewObject, kept simple for debugging; will never reach real output
     */
    public String printSimple() {
        return ParameterizedTypePrinter.print(InspectionProvider.DEFAULT, Qualification.EMPTY,
                this, false, Diamond.SHOW_ALL, false).toString();
    }

    /*
    used to compute a method's FQN
     */
    public String printForMethodFQN(InspectionProvider inspectionProvider, boolean varargs, Diamond diamond) {
        return ParameterizedTypePrinter.print(inspectionProvider, Qualification.FULLY_QUALIFIED_NAME,
                this, varargs, diamond, false, new HashSet<>()).toString();
    }

    /**
     * Stream for sending the qualified name to the KV store; for storing methods in the type map
     *
     * @param varArgs property of the type
     * @return the type as a fully qualified name, with type parameters according to the format
     * Tn or Mn, with n the index, and T for type, M for method
     */
    public String distinguishingName(InspectionProvider inspectionProvider, boolean varArgs) {
        return ParameterizedTypePrinter.print(inspectionProvider, Qualification.DISTINGUISHING_NAME,
                this, varArgs, Diamond.SHOW_ALL, false, new HashSet<>()).toString();
    }

    /*
    used in comparators, unevaluated method call
     */
    public String detailedString() {
        return ParameterizedTypePrinter.print(InspectionProvider.DEFAULT, Qualification.FULLY_QUALIFIED_NAME,
                this, false, Diamond.SHOW_ALL, false).toString();
    }

    /*
     used in ASM
     */
    public String detailedString(InspectionProvider inspectionProvider) {
        return ParameterizedTypePrinter.print(inspectionProvider, Qualification.FULLY_QUALIFIED_NAME,
                this, false, Diamond.SHOW_ALL, false).toString();
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
        if (Primitives.isVoidOrJavaLangVoid(this)) return false;
        if (typeInfo == null) return false;
        return Primitives.isPrimitiveExcludingVoid(typeInfo)
                || Primitives.isBoxedExcludingVoid(typeInfo)
                || Primitives.isJavaLangString(typeInfo);
    }

    // ******************************************************************************************************

    /*
     Given a concrete type (List<String>) make a map from the type's abstract parameters to its concrete ones (E -> String)
     */
    public Map<NamedType, ParameterizedType> initialTypeParameterMap(InspectionProvider inspectionProvider) {
        if (!isType()) return Map.of();
        if (parameters.isEmpty()) return Map.of();
        ParameterizedType originalType = typeInfo.asParameterizedType(inspectionProvider);
        int i = 0;
        // linkedHashMap to maintain an order for testing
        Map<NamedType, ParameterizedType> map = new LinkedHashMap<>();
        for (ParameterizedType parameter : originalType.parameters) {
            ParameterizedType recursive;
            if (parameter.isTypeParameter()) {
                recursive = parameters.get(i);
                map.put(parameter.typeParameter, recursive);
            } else if (parameter.isType()) {
                recursive = parameter;
            } else throw new UnsupportedOperationException();
            if (recursive != null && recursive.isType()) {
                Map<NamedType, ParameterizedType> recursiveMap = recursive.initialTypeParameterMap(inspectionProvider);
                map.putAll(recursiveMap);
            }
            i++;
        }
        return map;
    }

    /*
    HashMap<K, V> implements Map<K, V>
    Given Map<K, V>, go from abstract to concrete (HM:K to Map:K, HM:V to Map:V)
    */
    public Map<NamedType, ParameterizedType> forwardTypeParameterMap(InspectionProvider inspectionProvider) {
        if (!isType()) return Map.of();
        if (parameters.isEmpty()) return Map.of();
        ParameterizedType originalType = typeInfo.asParameterizedType(inspectionProvider); // Map:K, Map:V
        assert originalType.parameters.size() == parameters.size();
        int i = 0;
        // linkedHashMap to maintain an order for testing
        Map<NamedType, ParameterizedType> map = new LinkedHashMap<>();
        for (ParameterizedType parameter : originalType.parameters) {
            ParameterizedType p = parameters.get(i);
            if (p.isTypeParameter()) {
                map.put(p.typeParameter, parameter);
            }
            i++;
        }
        return map;
    }

    /*
    Starting from a formal type (List<E>), fill in a translation map given a concrete type (List<String>)
    IMPORTANT: the formal type has to have its formal parameters present, i.e., starting from TypeInfo,
    you should call this method on typeInfo.asParameterizedType(inspectionProvider) to ensure all formal
    parameters are present in this object.

    In the case of functional interfaces, this method goes via the SAM, avoiding the need of a formal implementation
    of the interface (i.e., a functional interface can have a SAM which is a function (1 argument, 1 return type)
    without explicitly implementing java.lang.function.Function)

    The third parameter decides the direction of the relation between the formal and the concrete type.
    When called from ParseMethodCallExpr, for example, 'this' is the parameter's formal parameter, and the concrete
    type has to be assignable to it.
     */

    public Map<NamedType, ParameterizedType> translateMap(InspectionProvider inspectionProvider,
                                                          ParameterizedType concreteType,
                                                          boolean concreteTypeIsAssignableToThis) {
        if (parameters.isEmpty()) {
            if (isTypeParameter()) {
                // T <-- String
                return Map.of(this.typeParameter, concreteType);
            }
            // String <-- String, no translation map
            return Map.of();
        }
        assert typeInfo != null;
        // no hope if Object or unbound wildcard is the best we have
        if (concreteType.typeInfo == null || Primitives.isJavaLangObject(concreteType)) return Map.of();

        if (isFunctionalInterface(inspectionProvider) && concreteType.isFunctionalInterface(inspectionProvider)) {
            return translationMapForFunctionalInterfaces(inspectionProvider, concreteType, concreteTypeIsAssignableToThis);
        }

        Map<NamedType, ParameterizedType> mapOfConcreteType = concreteType.initialTypeParameterMap(inspectionProvider);
        Map<NamedType, ParameterizedType> formalMap;
        if (typeInfo == concreteType.typeInfo) {
            // see Lambda_8 Stream<R>, R from flatmap -> Stream<T>
            formalMap = forwardTypeParameterMap(inspectionProvider);
        } else if (concreteTypeIsAssignableToThis) {
            // this is the super type (Set), concrete type is the sub-type (HashSet)
            formalMap = concreteType.typeInfo.mapInTermsOfParametersOfSuperType(inspectionProvider, this);
        } else {
            // concrete type is the super type, we MUST work towards the supertype!
            formalMap = typeInfo.mapInTermsOfParametersOfSubType(inspectionProvider, concreteType);
        }
        if (formalMap == null) return mapOfConcreteType;
        return TypeInfo.combineMaps(mapOfConcreteType, formalMap);
    }

    // TODO write tests!
    private Map<NamedType, ParameterizedType> translationMapForFunctionalInterfaces(InspectionProvider inspectionProvider,
                                                                                    ParameterizedType concreteType,
                                                                                    boolean concreteTypeIsAssignableToThis) {
        Map<NamedType, ParameterizedType> res = new HashMap<>();
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
            res.putAll(abstractTypeParameter.translateMap(inspectionProvider, concreteTypeParameter, concreteTypeIsAssignableToThis));
        }
        // and now the return type
        ParameterizedType myReturnType = methodTypeParameterMap.getConcreteReturnType();
        ParameterizedType concreteReturnType = concreteTypeMap.getConcreteReturnType();
        res.putAll(myReturnType.translateMap(inspectionProvider, concreteReturnType, concreteTypeIsAssignableToThis));
        return res;
    }


    /*
    Typical example: Set<String> set = new HashSet<>();
    'this' is the formal type of HashSet, the result should be a concrete version based on the concrete type 'Set<String>' given
    as a parameter.

    In this situation, 'this' is assignable to the concrete type, rather than the other way around.

    In case of concreteType.typeInfo == this.typeInfo, the 'initialTypeParameterMap' method would suffice.
    Otherwise, we need to make use of the TypeInfo.mapInTermsOfParametersOfSuperType method.
    Finally, the case of functional interfaces may have no formal link between the two types; it is dealt with separately.
     */
    public ParameterizedType inferDiamondNewObjectCreation(InspectionProvider inspectionProvider, ParameterizedType concreteType) {
        // new T<> is not allowed
        assert typeInfo != null;
        // HashSet<String> set = new HashSet<>();  both have the same type; no translation is needed
        if (typeInfo == concreteType.typeInfo) return concreteType;

        Map<NamedType, ParameterizedType> typeParameterMap = translateMap(inspectionProvider, concreteType, false);
        return MethodTypeParameterMap.apply(typeParameterMap, this);
    }

    /*
    'this' is the formal type of a field f in type A<T>; the concrete scope type is the scope of a field reference b.f.
    Here f may have a type parameter such as in

    class A<T> {
      public final List<T> f = ...;
    }

    The scope b could be an instance
        b = new A<String>(...)
    or,

    class B extends A<String> { ... } b = new B()

    We therefore want to apply a type parameter map on f's formal type. This map should go from the A's formal type parameters
    to the concrete type parameters of A or a type assignable to A.
    Note that it is perfectly possible that f's formal type is not parameterized at all (that's the most common situation)!

    in MethodCall_12, pair.k from pair 1st to K
     */
    public ParameterizedType inferConcreteFieldTypeFromConcreteScope(InspectionProvider inspectionProvider,
                                                                     ParameterizedType formalScopeType,
                                                                     ParameterizedType concreteScopeType) {
        if (typeParameter == null && parameters.isEmpty()) return this;

        Map<NamedType, ParameterizedType> typeParameterMap = formalScopeType.translateMap(inspectionProvider, concreteScopeType, true);
        return MethodTypeParameterMap.apply(typeParameterMap, this);
    }


    public boolean isNotAssignableFromTo(InspectionProvider inspectionProvider, ParameterizedType type) {
        return !new IsAssignableFrom(inspectionProvider, this, type).execute()
                && !new IsAssignableFrom(inspectionProvider, type, this).execute();
    }

    public boolean isAssignableFrom(InspectionProvider inspectionProvider, ParameterizedType type) {
        return new IsAssignableFrom(inspectionProvider, this, type).execute();
    }

    /**
     * Input: this == List&lt;String&gt;, superType == Iterable&lt;E&gt;;
     * List <- Collection <- Iterable;
     * Returns: Iterable&lt;String&gt;
     *
     * @param inspectionProvider the inspection provider
     * @param superType          the target super type
     * @return concrete version of the super type; null when the target cannot be found
     */
    public ParameterizedType concreteSuperType(InspectionProvider inspectionProvider,
                                               ParameterizedType superType) {
        TypeInfo bestType = bestTypeInfo(inspectionProvider);
        if (bestType == superType.typeInfo) {
            // if we start with Iterable<String>, and we're aiming for Iterable<E>, then
            // Iterable<String> is the right answer
            return this;
        }
        TypeInspection inspection = inspectionProvider.getTypeInspection(bestType);
        if (!Primitives.isJavaLangObject(inspection.parentClass())) {
            if (inspection.parentClass().typeInfo == superType.typeInfo) {
                return concreteDirectSuperType(inspectionProvider, inspection.parentClass());
            }
            /* do a recursion, but accept that we may return null
            we must call concreteSuperType on a concrete version of the parentClass
            */
            ParameterizedType res = inspection.parentClass().concreteSuperType(inspectionProvider, superType);
            if (res != null) {
                return concreteDirectSuperType(inspectionProvider, res);
            }
        }
        for (ParameterizedType interfaceType : inspection.interfacesImplemented()) {
            if (interfaceType.typeInfo == superType.typeInfo) {
                return concreteDirectSuperType(inspectionProvider, interfaceType);
            }
            // similar to parent
            ParameterizedType res = interfaceType.concreteSuperType(inspectionProvider, superType);
            if (res != null) {
                return concreteDirectSuperType(inspectionProvider, res);
            }
        }
        return null;
    }

    /*
    LinkedList<String>; LinkedList <-- List; parent type = List<E>; result: List<String>
     */
    public ParameterizedType concreteDirectSuperType(InspectionProvider inspectionProvider,
                                                     ParameterizedType parentType) {
        if (parentType.parameters.isEmpty()) return parentType;

        Map<NamedType, ParameterizedType> map = initialTypeParameterMap(inspectionProvider);
        ParameterizedType formalType = parentType.typeInfo.asParameterizedType(inspectionProvider);
        List<ParameterizedType> newParameters = new ArrayList<>(formalType.parameters.size());
        int i = 0;
        for (ParameterizedType param : formalType.parameters) {
            ParameterizedType formalInParentType = parentType.parameters.get(i);
            ParameterizedType result;
            if (formalInParentType.typeInfo != null) {
                result = formalInParentType;
            } else if (formalInParentType.typeParameter != null) {
                result = map.get(formalInParentType.typeParameter);
            } else {
                result = param;
            }
            newParameters.add(result == null ? param : result);
            i++;
        }
        return new ParameterizedType(parentType.typeInfo, List.copyOf(newParameters));
    }

    public boolean isFunctionalInterface() {
        if (typeInfo == null) return false;
        return typeInfo.typeInspection.get(typeInfo.fullyQualifiedName).isFunctionalInterface();
    }

    public boolean isFunctionalInterface(InspectionProvider inspectionProvider) {
        if (typeInfo == null) return false;
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        return typeInspection.isFunctionalInterface();
    }

    public boolean isUnboundTypeParameter() {
        return isUnboundTypeParameter(InspectionProvider.DEFAULT);
    }

    public boolean isUnboundTypeParameter(InspectionProvider inspectionProvider) {
        return arrays == 0 && bestTypeInfo(inspectionProvider) == null;
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
            return new MethodTypeParameterMap(theMethod.get(), initialTypeParameterMap(inspectionProvider));
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

    public DV getProperty(AnalysisProvider analysisProvider, Property property) {
        TypeInfo bestType = bestTypeInfo();
        if (bestType != null) {
            return analysisProvider.getTypeAnalysis(bestType).getProperty(property);
        }
        return property.falseDv;
    }

    public TypeInfo bestTypeInfo() {
        return bestTypeInfo(InspectionProvider.DEFAULT);
    }

    public TypeInfo bestTypeInfo(InspectionProvider inspectionProvider) {
        if (typeInfo != null) return typeInfo;
        if (typeParameter != null) {
            if (wildCard == WildCard.EXTENDS && parameters.size() == 1) {
                return parameters.get(0).bestTypeInfo();
            }
            TypeParameter definition;
            if (typeParameter.getOwner().isLeft()) {
                TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeParameter.getOwner().getLeft());
                definition = typeInspection.typeParameters().get(typeParameter.getIndex());
            } else {
                MethodInspection methodInspection = inspectionProvider.getMethodInspection(typeParameter.getOwner().getRight());
                definition = methodInspection.getTypeParameters().get(typeParameter.getIndex());
            }
            if (!definition.getTypeBounds().isEmpty()) {
                // IMPROVE should be a joint type
                return definition.getTypeBounds().get(0).typeInfo;
            }
        }
        return null;
    }

    /**
     * return the best common type
     *
     * @param other the other type
     * @return the common type
     */
    public ParameterizedType commonType(InspectionProvider inspectionProvider, ParameterizedType other) {
        assert other != null;

        if (equals(other)) return this;

        TypeInfo bestType = bestTypeInfo(inspectionProvider);
        TypeInfo otherBestType = other.bestTypeInfo(inspectionProvider);

        boolean isPrimitive = Primitives.isPrimitiveExcludingVoid(this);
        boolean otherIsPrimitive = Primitives.isPrimitiveExcludingVoid(other);
        if (isPrimitive && otherIsPrimitive) {
            return inspectionProvider.getPrimitives().widestType(this, other);
        }
        boolean isBoxed = Primitives.isBoxedExcludingVoid(this);
        boolean otherIsBoxed = Primitives.isBoxedExcludingVoid(other);
        if ((isPrimitive || isBoxed) && other == ParameterizedType.NULL_CONSTANT) {
            if (isBoxed) return this;
            return inspectionProvider.getPrimitives().boxed(bestType).asParameterizedType(inspectionProvider);
        }
        if ((otherIsPrimitive || otherIsBoxed) && this == ParameterizedType.NULL_CONSTANT) {
            if (otherIsBoxed) return other;
            return inspectionProvider.getPrimitives().boxed(otherBestType).asParameterizedType(inspectionProvider);
        }
        if (isPrimitive || otherIsPrimitive) {
            if (isPrimitive && otherIsBoxed && inspectionProvider.getPrimitives().boxed(bestType).equals(otherBestType)) {
                return other;
            }
            if (otherIsPrimitive && isBoxed && inspectionProvider.getPrimitives().boxed(otherBestType).equals(bestType)) {
                return this;
            }
            return inspectionProvider.getPrimitives().objectParameterizedType; // no common type
        }
        if (other == ParameterizedType.NULL_CONSTANT) return this;
        if (this == ParameterizedType.NULL_CONSTANT) return other;

        if (bestType == null || otherBestType == null)
            return inspectionProvider.getPrimitives().objectParameterizedType; // no common type
        if (isAssignableFrom(inspectionProvider, other)) {
            return this;
        }
        if (other.isAssignableFrom(inspectionProvider, this)) {
            return other;
        }
        // FIXME go into hierarchy
        return inspectionProvider.getPrimitives().objectParameterizedType; // no common type
    }

    public DV isTransparentOrAtLeastEventuallyE2Immutable(AnalysisProvider analysisProvider, TypeInfo typeBeingAnalysed) {
        if (arrays > 0) return Level.FALSE_DV;
        DV atLeastEventuallyE2Immutable = isAtLeastEventuallyE2Immutable(analysisProvider);
        if (atLeastEventuallyE2Immutable.valueIsTrue()) return Level.TRUE_DV;
        DV transparent = isTransparent(analysisProvider, typeBeingAnalysed);
        if (transparent.valueIsTrue()) return Level.TRUE_DV;
        if (transparent.isDelayed() || atLeastEventuallyE2Immutable.isDelayed()) {
            return transparent.min(atLeastEventuallyE2Immutable);
        }
        return Level.FALSE_DV;
    }

    private DV isAtLeastEventuallyE2Immutable(AnalysisProvider analysisProvider) {
        TypeInfo bestType = bestTypeInfo();
        if (bestType == null) return Level.FALSE_DV;
        DV immutable = analysisProvider.getTypeAnalysis(bestType).getProperty(Property.IMMUTABLE);
        if (immutable.isDelayed()) return immutable;
        return Level.fromBoolDv(MultiLevel.isAtLeastEventuallyE2Immutable(immutable));
    }

    public DV isTransparent(AnalysisProvider analysisProvider, TypeInfo typeBeingAnalysed) {
        TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(typeBeingAnalysed);
        SetOfTypes hiddenContentTypes = typeAnalysis.getTransparentTypes();
        if (hiddenContentTypes == null) return new CausesOfDelay.SimpleSet(new Location(typeBeingAnalysed),
                CauseOfDelay.Cause.HIDDEN_CONTENT);
        return Level.fromBoolDv(hiddenContentTypes.contains(this));
    }

    public DV canBeModifiedInThisClass(AnalysisProvider analysisProvider) {
        TypeInfo bestType = bestTypeInfo();
        if (bestType == null) return Level.FALSE_DV;
        DV immutable = analysisProvider.getTypeAnalysis(bestType).getProperty(Property.IMMUTABLE);
        if (immutable.isDelayed()) return immutable;
        boolean canBeModified = MultiLevel.isAtLeastEventuallyE2Immutable(immutable);
        return Level.fromBoolDv(canBeModified);
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

    public boolean equalsIgnoreArrays(ParameterizedType other) {
        return Objects.equals(typeInfo, other.typeInfo)
                && Objects.equals(typeParameter, other.typeParameter)
                && Objects.equals(parameters, other.parameters) && wildCard == other.wildCard;
    }

    public DV defaultNotNull() {
        return Primitives.isPrimitiveExcludingVoid(this) ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : MultiLevel.NULLABLE_DV;
    }

    public DV defaultContainer(AnalysisProvider analysisProvider) {
        TypeInfo bestType = bestTypeInfo();
        if (arrays > 0) {
            return Level.TRUE_DV;
        }
        if (bestType == null) {
            // unbound type parameter, null constant
            return Level.FALSE_DV;
        }
        TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return typeAnalysisNotAvailable(bestType);
        }
        return typeAnalysis.getProperty(Property.CONTAINER);
    }

    private static DV typeAnalysisNotAvailable(TypeInfo bestType) {
        return new CausesOfDelay.SimpleSet(bestType, CauseOfDelay.Cause.TYPE_ANALYSIS);
    }

    public DV defaultIndependent(AnalysisProvider analysisProvider) {
        TypeInfo bestType = bestTypeInfo();
        if (arrays > 0) {
            // because the "fields" of the array, i.e. the cells, can be mutated
            return MultiLevel.DEPENDENT_DV;
        }
        if (bestType == null) {
            // unbound type parameter, null constant
            return MultiLevel.INDEPENDENT_1_DV;
        }
        TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return typeAnalysisNotAvailable(bestType);
        }
        DV baseValue = typeAnalysis.getProperty(Property.INDEPENDENT);
        if (baseValue.isDelayed()) return baseValue;
        if (MultiLevel.isAtLeastE2Immutable(baseValue) && !parameters.isEmpty()) {
            DV doSum = typeAnalysis.immutableCanBeIncreasedByTypeParameters();
            if (doSum.valueIsTrue()) {
                DV paramValue = parameters.stream()
                        .map(pt -> pt.defaultIndependent(analysisProvider))
                        .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
                if (paramValue.isDelayed()) return paramValue;
                return MultiLevel.sumImmutableLevels(baseValue, paramValue);
            }
            if (doSum.isDelayed()) {
                return doSum;
            }
        }
        return baseValue;
    }

    public DV immutableOfHiddenContent(AnalysisProvider analysisProvider, boolean returnValueOfMethod) {
        TypeInfo bestType = bestTypeInfo();
        if (arrays > 0) {
            ParameterizedType withoutArrays = copyWithoutArrays();
            return withoutArrays.defaultImmutable(analysisProvider, returnValueOfMethod);
        }
        if (bestType == null) {
            return returnValueOfMethod ? MultiLevel.NOT_INVOLVED_DV : MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV;
        }
        TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return typeAnalysisNotAvailable(bestType);
        }
        SetOfTypes hiddenContentTypes = typeAnalysis.getTransparentTypes(this);
        if (hiddenContentTypes == null) {
            return new CausesOfDelay.SimpleSet(bestType, CauseOfDelay.Cause.HIDDEN_CONTENT);
        }
        return hiddenContentTypes.types().stream()
                .map(pt -> pt.defaultImmutable(analysisProvider, returnValueOfMethod))
                .reduce(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV::min);
    }

    public DV defaultImmutable(AnalysisProvider analysisProvider, boolean unboundIsMutable) {
        return defaultImmutable(analysisProvider, unboundIsMutable, MultiLevel.NOT_INVOLVED_DV);
    }

    /*
    Why dynamic value? firstEntry() returns a Map.Entry, which formally is MUTABLE, but has E2IMMUTABLE assigned
    Once a type is E2IMMUTABLE, we have to look at the immutability of the hidden content, to potentially upgrade
    to a higher version. See e.g., E2Immutable_11,12
     */
    public DV defaultImmutable(AnalysisProvider analysisProvider, boolean unboundIsMutable, DV dynamicValue) {
        assert dynamicValue.isDone();
        if (arrays > 0) {
            return MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV;
        }
        TypeInfo bestType = bestTypeInfo();
        if (bestType == null) {
            // unbound type parameter, null constant
            return dynamicValue.max(unboundIsMutable ? MultiLevel.NOT_INVOLVED_DV : MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV);
        }
        TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return typeAnalysisNotAvailable(bestType);
        }
        DV baseValue = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (baseValue.isDelayed()) {
            return baseValue;
        }
        DV dynamicBaseValue = dynamicValue.max(baseValue);
        if (MultiLevel.isAtLeastE2Immutable(dynamicBaseValue) && !parameters.isEmpty()) {
            DV doSum = typeAnalysis.immutableCanBeIncreasedByTypeParameters();
            if (doSum.isDelayed()) {
                assert typeAnalysis.isNotContracted();
                return doSum;
            }
            if (doSum.valueIsTrue()) {
                DV paramValue = parameters.stream()
                        .map(pt -> pt.defaultImmutable(analysisProvider, true))
                        .map(v -> v.containsCauseOfDelay(CauseOfDelay.Cause.TYPE_ANALYSIS) ? MultiLevel.MUTABLE_DV : v)
                        .reduce(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV::min);
                if (paramValue.isDelayed()) return paramValue;
                return MultiLevel.sumImmutableLevels(dynamicBaseValue, paramValue);
            }
        }
        return dynamicBaseValue;
    }

    // for delay debugging
    public String fullyQualifiedName() {
        return detailedString();
    }

    public static boolean isUnboundTypeParameterOrJLO(TypeInfo bestType) {
        return bestType == null || Primitives.isJavaLangObject(bestType);
    }

    // if we arrive here with Set<String>, we need Collection<String>, Iterable<String>, JLO in the result
    public Stream<ParameterizedType> concreteSuperTypes(InspectionProvider inspectionProvider) {
        TypeInfo bestType = bestTypeInfo(inspectionProvider);
        if (bestType == null || Primitives.isJavaLangObject(bestType)) return Stream.of();
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(bestType);
        Stream<ParameterizedType> recursiveFromParent;
        if (!Primitives.isJavaLangObject(typeInspection.parentClass())) {
            ParameterizedType concreteParentType = concreteSuperType(inspectionProvider, typeInspection.parentClass());
            recursiveFromParent = Stream.concat(Stream.of(concreteParentType),
                    concreteParentType.concreteSuperTypes(inspectionProvider));
        } else {
            ParameterizedType concreteParentType = inspectionProvider.getPrimitives().objectParameterizedType;
            recursiveFromParent = Stream.of(concreteParentType);
        }
        Stream<ParameterizedType> concreteInterfaceTypes = Stream.of();
        for (ParameterizedType interfaceType : typeInspection.interfacesImplemented()) {
            ParameterizedType concreteInterfaceType = concreteSuperType(inspectionProvider, interfaceType);
            concreteInterfaceTypes = Stream.concat(Stream.of(concreteInterfaceType),
                    concreteInterfaceType.concreteSuperTypes(inspectionProvider));
        }
        return Stream.concat(recursiveFromParent, concreteInterfaceTypes);
    }

    public boolean isUnboundWildcard() {
        return typeInfo == null && typeParameter == null;
    }

    public boolean isAbstractInJavaUtilFunction(InspectionProvider inspectionProvider) {
        TypeInfo bestType = bestTypeInfo(inspectionProvider);
        return bestType != null && bestType.isPrimaryType()
                && "java.util.function".equals(bestType.packageName())
                && bestType.isAbstract(inspectionProvider);
    }

    public Set<TypeParameter> extractTypeParameters() {
        if (typeParameter != null) return Set.of(typeParameter);
        if (typeInfo != null) {
            return parameters.stream().flatMap(p -> p.extractTypeParameters().stream()).collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    /**
     * IMPORTANT: code copied from MethodTypeParameterMap
     *
     * @param translate
     * @return
     */
    public ParameterizedType applyTranslation(Map<NamedType, ParameterizedType> translate) {
        ParameterizedType pt = this;
        while (pt.isTypeParameter() && translate.containsKey(pt.typeParameter)) {
            ParameterizedType newPt = translate.get(pt.typeParameter);
            if (newPt.equals(pt)) break;
            pt = newPt;
        }
        final ParameterizedType stablePt = pt;
        if (stablePt.parameters.isEmpty()) return stablePt;
        List<ParameterizedType> recursivelyMappedParameters = stablePt.parameters.stream()
                .map(x -> x == stablePt || x == this ? stablePt : x.applyTranslation(translate))
                .collect(Collectors.toList());
        if (stablePt.typeInfo == null) {
            throw new UnsupportedOperationException("? input " + stablePt + " has no type");
        }
        return new ParameterizedType(stablePt.typeInfo, recursivelyMappedParameters);
    }
}
