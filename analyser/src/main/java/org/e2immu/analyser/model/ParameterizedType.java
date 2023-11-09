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

import org.e2immu.analyser.inspector.MethodTypeParameterMap;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.expression.util.TranslationCollectors;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.PrimitivesWithoutParameterizedType;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.analyser.util.UpgradableIntMap;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParameterizedType {
    public static final ParameterizedType NULL_CONSTANT = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType RETURN_TYPE_OF_CONSTRUCTOR = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType NO_TYPE_GIVEN_IN_LAMBDA = new ParameterizedType(WildCard.NONE);
    public static final ParameterizedType WILDCARD_PARAMETERIZED_TYPE = new ParameterizedType(WildCard.UNBOUND);
    public static final ParameterizedType TYPE_OF_EMPTY_EXPRESSION = new ParameterizedType(WildCard.NONE);

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
        assert checkParametersForPrimitives();
    }

    public ParameterizedType(TypeInfo typeInfo, List<ParameterizedType> parameters, int arrays) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.parameters = List.copyOf(Objects.requireNonNull(parameters));
        this.typeParameter = null;
        this.arrays = arrays;
        this.wildCard = WildCard.NONE;
        assert checkParametersForPrimitives();
    }

    public boolean isPrimitiveExcludingVoid() {
        return arrays == 0 && typeInfo != null && typeInfo.isPrimitiveExcludingVoid();
    }

    public boolean isPrimitiveStringClass() {
        return arrays == 0 && typeInfo != null && (typeInfo.isPrimitiveExcludingVoid() || typeInfo.isJavaLangString() || typeInfo.isJavaLangClass());
    }

    private boolean checkParametersForPrimitives() {
        assert parameters.stream().noneMatch(ParameterizedType::isPrimitiveExcludingVoid) : "Type parameters contain primitives: " + parameters;
        return true;
    }

    // all-in method used by the ParameterizedTypeFactory in byte code inspector
    public ParameterizedType(TypeInfo typeInfo, int arrays, WildCard wildCard, List<ParameterizedType> typeParameters) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.arrays = arrays;
        this.wildCard = wildCard;
        this.parameters = List.copyOf(typeParameters);
        this.typeParameter = null;
        assert checkParametersForPrimitives();
    }

    private ParameterizedType(TypeInfo typeInfo, int arrays, WildCard wildCard, List<ParameterizedType> typeParameters, TypeParameter typeParameter) {
        this.typeInfo = typeInfo;
        this.arrays = arrays;
        this.wildCard = wildCard;
        this.parameters = typeParameters;
        this.typeParameter = typeParameter;
        assert checkParametersForPrimitives();
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
        if (typeInfo == null || typeInfo.isPrimitiveExcludingVoid()) {
            newTypeInfo = typeInfo;
        } else {
            newTypeInfo = Objects.requireNonNull(localTypeContext.typeMap.get(typeInfo.fullyQualifiedName),
                    "Cannot find " + typeInfo.fullyQualifiedName + " in typeStore");
        }
        List<ParameterizedType> newParameters = parameters.stream().map(pt -> pt.copy(localTypeContext)).collect(Collectors.toList());
        TypeParameter newTypeParameter = typeParameter == null ? null : (TypeParameter) localTypeContext.get(typeParameter.getName(), true);
        return new ParameterizedType(newTypeInfo, arrays, wildCard, newParameters, newTypeParameter);
    }

    public ParameterizedType copyWithOneFewerArrays() {
        if (arrays == 0) return this;
        return new ParameterizedType(this.typeInfo, arrays - 1, wildCard, parameters, typeParameter);
    }

    public ParameterizedType copyWithoutArrays() {
        if (arrays == 0) return this;
        return new ParameterizedType(this.typeInfo, 0, wildCard, parameters, typeParameter);
    }

    public ParameterizedType erased() {
        if (arrays == 0 && wildCard == WildCard.NONE && parameters.isEmpty()) return this;
        return new ParameterizedType(this.typeInfo, 0, WildCard.NONE, List.of(), typeParameter);
    }

    public ParameterizedType copyWithFewerArrays(int arrays) {
        if (arrays == 0) return this;
        int newArrays = this.arrays - arrays;
        assert newArrays >= 0 : "Trying to remove " + arrays + " arrays from " + this;
        return new ParameterizedType(this.typeInfo, newArrays, wildCard, parameters, typeParameter);
    }

    public ParameterizedType copyWithArrays(int arrays) {
        // the following check is important to maintain object '==' for the static types like NULL_CONSTANT
        if (arrays == this.arrays) return this;
        assert arrays >= 0;
        return new ParameterizedType(this.typeInfo, arrays, wildCard, parameters, typeParameter);
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
        // there's a significant risk at infinite loops because the parameters may contain cycles.
        return Objects.hash(typeInfo, parameters.size(), typeParameter, arrays, wildCard);
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
        if (isVoidOrJavaLangVoid()) return false;
        if (typeInfo == null) return false;
        return typeInfo.isPrimitiveExcludingVoid()
                || typeInfo.isBoxedExcludingVoid()
                || typeInfo.isJavaLangString();
    }

    // ******************************************************************************************************

    /*
     Given a concrete type (List<String>) make a map from the type's abstract parameters to its concrete ones (E -> String)

     If the abstract type contains self-references, we cannot recurse, because their type parameters have the same name...
     With visited, the method returns K=Integer, V=Map<Integer,String> when presented with Map<Integer,Map<Integer,String>>,
     without visited, it would recurse and return K=Integer, V=String
     */
    public Map<NamedType, ParameterizedType> initialTypeParameterMap(InspectionProvider inspectionProvider) {
        if (!isType()) return Map.of();
        if (parameters.isEmpty()) return Map.of();
        return initialTypeParameterMap(inspectionProvider, new HashSet<>());
    }

    private Map<NamedType, ParameterizedType> initialTypeParameterMap(InspectionProvider inspectionProvider, Set<TypeInfo> visited) {
        if (!isType()) return Map.of();
        visited.add(typeInfo);
        if (parameters.isEmpty()) return Map.of();
        ParameterizedType originalType = typeInfo.asParameterizedType(inspectionProvider);
        int i = 0;
        // linkedHashMap to maintain an order for testing
        Map<NamedType, ParameterizedType> map = new LinkedHashMap<>();
        for (ParameterizedType parameter : originalType.parameters) {
            ParameterizedType recursive;
            if (parameter.isTypeParameter()) {
                ParameterizedType pt = parameters.get(i);
                if (pt != null && pt.isUnboundWildcard() && !parameter.typeParameter.getTypeBounds().isEmpty()) {
                    // replace '?' by '? extends X', with 'X' the first type bound, see TypeParameter_3
                    recursive = new ParameterizedType(parameter.typeParameter.getTypeBounds().get(0).typeInfo,
                            WildCard.EXTENDS);
                } else {
                    recursive = pt;
                }
                map.put(parameter.typeParameter, recursive);
            } else if (parameter.isType()) {
                recursive = parameter;
            } else throw new UnsupportedOperationException();
            if (recursive != null && recursive.isType() && !visited.contains(recursive.typeInfo)) {
                Map<NamedType, ParameterizedType> recursiveMap = recursive.initialTypeParameterMap(inspectionProvider, visited);
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
                if (arrays > 0) {
                    if (concreteType.isFunctionalInterface(inspectionProvider)) {
                        // T[], Expression[]::new == IntFunction<Expression>
                        ParameterizedType arrayType = concreteType.findSingleAbstractMethodOfInterface(inspectionProvider)
                                .getConcreteReturnType(inspectionProvider.getPrimitives());
                        return Map.of(this.typeParameter, arrayType.copyWithFewerArrays(arrays));
                    }
                    // T <-- String,  T[],String[] -> T <-- String, T[],String[][] -> T <- String[]
                    if (concreteType.arrays > 0) {
                        return Map.of(this.typeParameter, concreteType.copyWithFewerArrays(arrays));
                    }
                }
                return Map.of(this.typeParameter, concreteType);
            }
            // String <-- String, no translation map
            return Map.of();
        }
        assert typeInfo != null;
        // no hope if Object or unbound wildcard is the best we have
        if (concreteType.typeInfo == null || concreteType.isJavaLangObject()) return Map.of();

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
    private Map<NamedType, ParameterizedType> translationMapForFunctionalInterfaces
    (InspectionProvider inspectionProvider,
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
        Primitives primitives = inspectionProvider.getPrimitives();
        for (int i = 0; i < methodParams.size(); i++) {
            ParameterizedType abstractTypeParameter = methodParams.get(i).parameterizedType;
            ParameterizedType concreteTypeParameter = concreteTypeMap.getConcreteTypeOfParameter(primitives, i);
            res.putAll(abstractTypeParameter.translateMap(inspectionProvider, concreteTypeParameter,
                    concreteTypeIsAssignableToThis));
        }
        // and now the return type
        ParameterizedType myReturnType = methodTypeParameterMap.getConcreteReturnType(primitives);
        ParameterizedType concreteReturnType = concreteTypeMap.getConcreteReturnType(primitives);
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
        return applyTranslation(inspectionProvider.getPrimitives(), typeParameterMap);
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
        return applyTranslation(inspectionProvider.getPrimitives(), typeParameterMap);
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
        ParameterizedType parentClass = inspection.parentClass();
        if (parentClass != null && !parentClass.isJavaLangObject()) {
            if (parentClass.typeInfo == superType.typeInfo) {
                return concreteDirectSuperType(inspectionProvider, parentClass);
            }
            /* do a recursion, but accept that we may return null
            we must call concreteSuperType on a concrete version of the parentClass
            */
            ParameterizedType res = parentClass.concreteSuperType(inspectionProvider, superType);
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
        if (typeInfo == null || arrays > 0) return false;
        return typeInfo.typeInspection.get(typeInfo.fullyQualifiedName).isFunctionalInterface();
    }

    public boolean isFunctionalInterface(InspectionProvider inspectionProvider) {
        if (typeInfo == null || arrays > 0) return false;
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        return typeInspection.isFunctionalInterface();
    }

    public boolean isUnboundTypeParameter() {
        return arrays == 0 && typeParameter != null && (typeParameter.getTypeBounds().isEmpty()
                || typeParameter.getTypeBounds().size() == 1 && typeParameter.getTypeBounds().get(0).isJavaLangObject());
    }

    public MethodTypeParameterMap findSingleAbstractMethodOfInterface(InspectionProvider inspectionProvider) {
        return findSingleAbstractMethodOfInterface(inspectionProvider, true);
    }

    /**
     * @param inspectionProvider necessary to find super types and methods
     * @param complain           If false, accept that we're in a functional interface, and do not complain. Only used in the recursion.
     *                           If true, then starting point of the recursion. We need a functional interface, and will complain at the end.
     * @return the combination of method and initial type parameter map
     */
    public MethodTypeParameterMap findSingleAbstractMethodOfInterface(InspectionProvider inspectionProvider, boolean complain) {
        if (complain && !isFunctionalInterface(inspectionProvider)) return null;
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        Optional<MethodInspection> theMethod = typeInspection.methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(m -> !m.isStatic() && !m.isDefault())
                .map(inspectionProvider::getMethodInspection)
                .findFirst();
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
            if (typeParameter.getOwner() != null) {
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
            } // else: in JFocus, we can temporarily have no owner during type generalization
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
        boolean isPrimitive = isPrimitiveExcludingVoid();
        boolean otherIsPrimitive = other.isPrimitiveExcludingVoid();
        Primitives primitives = inspectionProvider.getPrimitives();
        if (isPrimitive && otherIsPrimitive) {
            return primitives.widestType(this, other);
        }
        boolean isBoxed = isBoxedExcludingVoid();
        boolean otherIsBoxed = other.isBoxedExcludingVoid();

        if ((isPrimitive || isBoxed) && other == ParameterizedType.NULL_CONSTANT) {
            if (isBoxed) return this;
            return primitives.boxed(bestType).asParameterizedType(inspectionProvider);
        }
        if ((otherIsPrimitive || otherIsBoxed) && this == ParameterizedType.NULL_CONSTANT) {
            if (otherIsBoxed) return other;
            return primitives.boxed(otherBestType).asParameterizedType(inspectionProvider);
        }
        if (isPrimitive || otherIsPrimitive) {
            /* one is boxed, the other is not. The result must be boxed (see e.g.
            org.e2immu.analyser.model.value.TestEqualsConstantInline.test17)
             */
            if (isPrimitive && otherIsBoxed) {
                TypeInfo otherUnboxed = primitives.unboxed(otherBestType);
                ParameterizedType otherUnboxedPt = otherUnboxed.asSimpleParameterizedType();
                if (equals(otherUnboxedPt)) return primitives.boxed(bestType).asParameterizedType(inspectionProvider);
                if (primitives.isAssignableFromTo(this, otherUnboxedPt, true) >= 0 ||
                        primitives.isAssignableFromTo(otherUnboxedPt, this, true) >= 0) {
                    return primitives.boxed(primitives.widestType(this, otherUnboxedPt).typeInfo).asSimpleParameterizedType();
                }
            }
            if (otherIsPrimitive && isBoxed) {
                TypeInfo unboxed = primitives.unboxed(bestType);
                ParameterizedType unboxedPt = unboxed.asSimpleParameterizedType();
                if (unboxedPt.equals(other)) return this;
                if (primitives.isAssignableFromTo(other, unboxedPt, true) >= 0 ||
                        primitives.isAssignableFromTo(unboxedPt, other, true) >= 0) {
                    return primitives.boxed(primitives.widestType(other, unboxedPt).typeInfo).asSimpleParameterizedType();
                }
            }
            return primitives.objectParameterizedType(); // no common type
        }
        if (other == ParameterizedType.NULL_CONSTANT) return this;
        if (this == ParameterizedType.NULL_CONSTANT) return other;

        if (bestType == null || otherBestType == null) {
            return primitives.objectParameterizedType(); // no common type
        }
        if (isAssignableFrom(inspectionProvider, other)) {
            return this;
        }
        if (other.isAssignableFrom(inspectionProvider, this)) {
            return other;
        }
        // go into the hierarchy
        Map<TypeInfo, Integer> hierarchy = bestType.makeHierarchy(inspectionProvider);
        Map<TypeInfo, Integer> otherHierarchy = otherBestType.makeHierarchy(inspectionProvider);
        List<TypeInfo> common = new ArrayList<>(hierarchy.keySet());
        common.retainAll(otherHierarchy.keySet());
        if (common.isEmpty()) {
            return primitives.objectParameterizedType();
        }
        if (common.size() > 1) {
            common.sort(Comparator.comparingInt(hierarchy::get));
        }
        TypeInfo commonSuperType = common.get(0);
        if (commonSuperType.equals(bestType)) {
            return this;
        }
        if (commonSuperType.equals(otherBestType)) {
            return other;
        }
        ParameterizedType result = commonSuperType.asParameterizedType(inspectionProvider);
        TypeInspection commonInspection = inspectionProvider.getTypeInspection(commonSuperType);
        if (!commonInspection.typeParameters().isEmpty()) {
            ParameterizedType concrete = concreteSuperType(inspectionProvider, result);
            ParameterizedType concreteOther = other.concreteSuperType(inspectionProvider, result);
            List<ParameterizedType> updatedParameters = new ArrayList<>(commonInspection.typeParameters().size());
            int i = 0;
            for (ParameterizedType parameter : concrete.parameters) {
                ParameterizedType otherParameter = concreteOther.parameters.get(i++);
                ParameterizedType commonParameter;
                if (equals(parameter) && other.equals(otherParameter)) {
                    // common situation when the types implement Comparable; must avoid infinite recursion
                    commonParameter = primitives.objectParameterizedType();
                } else {
                    commonParameter = parameter.commonType(inspectionProvider, otherParameter);
                }
                updatedParameters.add(commonParameter);
            }
            return new ParameterizedType(commonSuperType, updatedParameters, result.arrays);
        }
        return result;
    }


    public TypeInfo toBoxed(PrimitivesWithoutParameterizedType primitives) {
        return primitives.boxed(typeInfo);
    }

    public ParameterizedType mostSpecific(InspectionProvider inspectionProvider,
                                          TypeInfo primaryType,
                                          ParameterizedType other) {
        if (equals(other)) return this;
        if (isType() && typeInfo.isVoid() || other.isType() && other.typeInfo.isVoid()) {
            return inspectionProvider.getPrimitives().voidParameterizedType();
        }
        if (isTypeParameter()) {
            if (other.isTypeParameter()) {
                // a type parameter in the primary type has priority over another one
                // IMPROVE change this to a hierarchy rather than primary vs other
                if (primaryType.equals(other.typeParameter.primaryType())) return other;
                return this;
            }
            return other;
        }
        if (other.isTypeParameter() && !isTypeParameter()) return this;

        if (isBoxedExcludingVoid() && other.isPrimitiveExcludingVoid()) return other;
        if (other.isBoxedExcludingVoid() && isPrimitiveExcludingVoid()) return this;

        if (isAssignableFrom(inspectionProvider, other)) {
            return other;
        }
        return this;
    }

    public UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        return UpgradableBooleanMap.of(
                parameters.stream().flatMap(pt -> pt.typesReferenced(explicit).stream()).collect(UpgradableBooleanMap.collector()),
                isType() && !typeInfo.isPrimitiveExcludingVoid() ?
                        UpgradableBooleanMap.of(typeInfo, explicit) : UpgradableBooleanMap.of());
    }

    public UpgradableIntMap<TypeInfo> typesReferenced2(int weight) {
        return UpgradableIntMap.of(
                parameters.stream()
                        .flatMap(pt -> pt.typesReferenced2(weight).stream())
                        .collect(UpgradableIntMap.collector()),
                isType() && !typeInfo.isPrimitiveExcludingVoid()
                        ? UpgradableIntMap.of(typeInfo.primaryType(), weight)
                        : UpgradableIntMap.of());
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

    // for delay debugging
    public String fullyQualifiedName() {
        return detailedString();
    }

    public static boolean isUnboundTypeParameterOrJLO(TypeInfo bestType) {
        return bestType == null || bestType.isJavaLangObject();
    }

    // if we arrive here with Set<String>, we need Collection<String>, Iterable<String>, JLO in the result
    public Stream<ParameterizedType> concreteSuperTypes(InspectionProvider inspectionProvider) {
        TypeInfo bestType = bestTypeInfo(inspectionProvider);
        if (bestType == null || bestType.isJavaLangObject()) return Stream.of();
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(bestType);
        Stream<ParameterizedType> recursiveFromParent;
        ParameterizedType parentClass = typeInspection.parentClass();
        if (parentClass != null && !parentClass.isJavaLangObject()) {
            ParameterizedType concreteParentType = concreteSuperType(inspectionProvider, parentClass);
            recursiveFromParent = Stream.concat(Stream.of(concreteParentType),
                    concreteParentType.concreteSuperTypes(inspectionProvider));
        } else {
            ParameterizedType concreteParentType = inspectionProvider.getPrimitives().objectParameterizedType();
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

    public boolean hasTypeParameters() {
        if (typeParameter != null) return true;
        if (typeInfo != null) {
            return parameters.stream().anyMatch(ParameterizedType::hasTypeParameters);
        }
        return false;
    }

    /**
     * IMPORTANT: code copied from MethodTypeParameterMap
     *
     * @param translate the map to be applied on the type parameters of this
     * @return a newly created ParameterizedType
     */
    public ParameterizedType applyTranslation(PrimitivesWithoutParameterizedType primitives,
                                              Map<NamedType, ParameterizedType> translate) {
        if (translate.isEmpty()) return this;
        ParameterizedType pt = this;
        if (pt.isTypeParameter()) {
            boolean add = false;
            while (pt.isTypeParameter() && translate.containsKey(pt.typeParameter)) {
                ParameterizedType newPt = translate.get(pt.typeParameter);
                if (newPt.equals(pt) || newPt.isTypeParameter() && pt.typeParameter.equals(newPt.typeParameter)) break;
                pt = newPt;
                add = true;
            }
            // we want to add this.arrays only once, and only when there was a translation (MethodCall_61)
            if (add) {
                pt = pt.copyWithArrays(pt.arrays + arrays);
            }
        }
        // see MethodCall_60,_61,_62
        final ParameterizedType stablePt = pt;
        if (stablePt.parameters.isEmpty()) return stablePt;
        List<ParameterizedType> recursivelyMappedParameters = stablePt.parameters.stream()
                .map(x -> x == stablePt || x == this ? stablePt : x.applyTranslation(primitives, translate))
                .map(x -> x.ensureBoxed(primitives))
                .collect(Collectors.toList());
        if (stablePt.typeInfo == null) {
            throw new UnsupportedOperationException("? input " + stablePt + " has no type");
        }
        return new ParameterizedType(stablePt.typeInfo, recursivelyMappedParameters);
    }

    public ParameterizedType ensureBoxed(PrimitivesWithoutParameterizedType primitives) {
        if (isPrimitiveExcludingVoid() || isVoid()) {
            return toBoxed(primitives).asSimpleParameterizedType();
        }
        return this;
    }

    public boolean isBoolean() {
        if (arrays != 0) return false;
        return typeInfo != null && "boolean".equals(typeInfo.fullyQualifiedName);
    }

    public boolean isBooleanOrBoxedBoolean() {
        if (arrays != 0) return false;
        return typeInfo != null && (typeInfo.isBoolean() || typeInfo.isBoxedBoolean());
    }

    public boolean isNotBooleanOrBoxedBoolean() {
        if (typeInfo == null || arrays != 0) return true; // for parameterized types
        return !typeInfo.isBoolean() && !typeInfo.isBoxedBoolean();
    }

    public boolean isVoid() {
        if (this == TYPE_OF_EMPTY_EXPRESSION) return true;
        return arrays == 0 && typeInfo != null && typeInfo.isVoid();
    }

    public boolean isVoidOrJavaLangVoid() {
        if (isVoid()) return true;
        return arrays == 0 && typeInfo != null && typeInfo.isJavaLangVoid();
    }

    public boolean isJavaLangString() {
        return arrays == 0 && typeInfo != null && typeInfo.isJavaLangString();
    }

    public boolean isJavaLangObject() {
        return arrays == 0 && typeInfo != null && typeInfo.isJavaLangObject();
    }

    public boolean isBoxedExcludingVoid() {
        return arrays == 0 && typeInfo != null && typeInfo.isBoxedExcludingVoid();
    }

    public boolean isDiscrete() {
        if (arrays != 0 || typeInfo == null) return false;
        return typeInfo.isInt()
                || typeInfo.isInteger()
                || typeInfo.isLong()
                || typeInfo.isBoxedLong()
                || typeInfo.isShort()
                || typeInfo.isBoxedShort()
                || typeInfo.isByte()
                || typeInfo.isBoxedByte();
    }

    public boolean isNumeric() {
        return arrays == 0 && typeInfo != null && typeInfo.isNumeric();
    }

    public boolean isLong() {
        return arrays == 0 && typeInfo != null && typeInfo.isLong();
    }

    public boolean isInt() {
        return arrays == 0 && typeInfo != null && typeInfo.isInt();
    }

    public boolean isMathematicallyInteger() {
        return arrays == 0 && typeInfo != null && (
                typeInfo.isByte() || typeInfo.isBoxedByte() ||
                        typeInfo.isShort() || typeInfo.isBoxedShort() ||
                        typeInfo.isInt() || typeInfo.isInteger() ||
                        typeInfo.isLong() || typeInfo.isBoxedLong() ||
                        typeInfo.isChar() || typeInfo.isCharacter());
    }

    /*
    Let A, B be a type rather than a type parameter.
    A -> A
    A<T extends B> -> A<B>
    T extends A -> return A
    T extends Comparable<? super T> -> return Comparable, remove the T
    T === T extends Object -> return JLO

    when there are multiple type bounds,
     */
    public List<ParameterizedType> replaceByTypeBounds() {
        return replaceByTypeBounds(new HashSet<>());
    }

    /*
    MethodCall_32,_36,46,_47 show why this method is relatively complicated
     */
    private List<ParameterizedType> replaceByTypeBounds(Set<TypeParameter> remove) {
        if (typeInfo != null) {
            if (parameters.isEmpty()) {
                return List.of(this);
            }
            // the translation collector detects !=; returns 'parameters 'when there are no differences in the list
            List<ParameterizedType> updatedParameters = parameters.stream()
                    .filter(pt -> pt.typeParameter == null || !remove.contains(pt.typeParameter))
                    .flatMap(p -> p.replaceByTypeBounds(remove).stream())
                    .collect(TranslationCollectors.toList(parameters));
            if (updatedParameters == parameters) {
                return List.of(this);
            }
            return List.of(new ParameterizedType(typeInfo, updatedParameters));
        }
        if (typeParameter != null) {
            List<ParameterizedType> typeBounds = typeParameter.getTypeBounds();
            if (typeBounds.isEmpty()) {
                // a type parameter without type bounds; we can keep that one; IsAssignableFrom can deal with it
                return List.of(this);
            }
            Set<TypeParameter> newRemove = Stream.concat(remove.stream(), Stream.of(typeParameter))
                    .collect(Collectors.toUnmodifiableSet());
            return typeBounds
                    .stream().filter(pt -> pt.typeParameter == null || !remove.contains(pt.typeParameter))
                    .flatMap(p -> p.replaceByTypeBounds(newRemove).stream())
                    .map(p -> p.copyWithArrays(arrays))
                    .collect(TranslationCollectors.toList(typeBounds));
        }
        return List.of(this);
    }

    // initially implemented to ensure that a NULL type doesn't overwrite a valid type, see Lambda_17
    public ParameterizedType bestDefined(ParameterizedType other) {
        if (typeInfo != null && other.typeInfo == null) return this;
        if (other.typeInfo != null && typeInfo == null) return other;
        if (typeInfo != null) {
            return this; // TODO? should we go recursively?
        }
        if (typeParameter != null && other.typeParameter == null) return this;
        if (typeParameter == null && other.typeParameter != null) return other;
        return this;// doesn't matter anymore
    }
}
