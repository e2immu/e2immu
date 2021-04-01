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

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.ExpandableAnalyserContextImpl;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.inspector.MethodTypeParameterMap;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;

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
                this, varargs, diamond, false).toString();
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
                this, varArgs, Diamond.SHOW_ALL, false).toString();
    }

    /*
    used in ASM, comparators, unevaluated method call
     */
    public String detailedString() {
        return ParameterizedTypePrinter.print(InspectionProvider.DEFAULT, Qualification.FULLY_QUALIFIED_NAME,
                this, false, Diamond.SHOW_ALL, false).toString();
    }

    /*
    for logging purposes only
     */
    public String detailedStringLogDuringInspection(InspectionProvider inspectionProvider) {
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
        if (Primitives.isVoid(this)) return false;
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
            if (parameter.isTypeParameter()) {
                map.put(parameter.typeParameter, parameters.get(i));
            } else if (parameter.isType()) {
                Map<NamedType, ParameterizedType> recursive = parameter.initialTypeParameterMap(inspectionProvider);
                map.putAll(recursive);
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
        if (concreteTypeIsAssignableToThis) {
            assert isAssignableFrom(inspectionProvider, concreteType);
        } else {
            assert concreteType.isAssignableFrom(inspectionProvider, this);
        }

        if (parameters.isEmpty()) {
            if (isTypeParameter()) {
                // T <-- String
                return Map.of(this.typeParameter, concreteType);
            }
            // String <-- String, no translation map
            return Map.of();
        }
        assert typeInfo != null;
        assert concreteType.typeInfo != null;

        if (isFunctionalInterface(inspectionProvider) && concreteType.isFunctionalInterface(inspectionProvider)) {
            return translationMapForFunctionalInterfaces(inspectionProvider, concreteType, concreteTypeIsAssignableToThis);
        }

        Map<NamedType, ParameterizedType> mapOfConcreteType = concreteType.initialTypeParameterMap(inspectionProvider);
        if (typeInfo == concreteType.typeInfo) return mapOfConcreteType;
        Map<NamedType, ParameterizedType> formalMap;
        if (concreteTypeIsAssignableToThis) {
            // this is the super type (Set), concrete type is the sub-type (HashSet)
            formalMap = concreteType.typeInfo.mapInTermsOfParametersOfSuperType(inspectionProvider, typeInfo);
        } else {
            // concrete type is the super type, we MUST work towards the supertype!
            formalMap = typeInfo.mapInTermsOfParametersOfSubType(inspectionProvider, concreteType.typeInfo);
        }
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
     */
    public ParameterizedType inferConcreteFieldTypeFromConcreteScope(InspectionProvider inspectionProvider,
                                                                     ParameterizedType formalScopeType,
                                                                     ParameterizedType concreteScopeType) {
        if (parameters.isEmpty()) return this;

        Map<NamedType, ParameterizedType> typeParameterMap = formalScopeType.translateMap(inspectionProvider, concreteScopeType, true);
        return MethodTypeParameterMap.apply(typeParameterMap, this);
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
                        return checkBoxing(inspectionProvider, type.typeInfo) ? BOXING_FROM_PRIMITIVE : NOT_ASSIGNABLE;
                    }
                    // TODO; for now: primitive array can only be assigned to its own type
                    return NOT_ASSIGNABLE;
                }
                if (Primitives.isPrimitiveExcludingVoid(this)) {
                    // the other one is not a primitive
                    return arrays == 0 && type.checkBoxing(inspectionProvider, typeInfo) ? BOXING_TO_PRIMITIVE : NOT_ASSIGNABLE;
                }

                TypeInspection typeInspection = inspectionProvider.getTypeInspection(type.typeInfo);
                for (ParameterizedType interfaceImplemented : typeInspection.interfacesImplemented()) {
                    int scoreInterface = numericIsAssignableFrom(inspectionProvider, interfaceImplemented, true);
                    if (scoreInterface != NOT_ASSIGNABLE) return IN_HIERARCHY + scoreInterface;
                }
                ParameterizedType parentClass = typeInspection.parentClass();
                if (parentClass != null && !Primitives.isJavaLangObject(parentClass)) {
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

    private boolean checkBoxing(InspectionProvider inspectionProvider, TypeInfo primitiveType) {
        TypeInfo boxed = primitiveType.asParameterizedType(inspectionProvider).toBoxed(inspectionProvider.getPrimitives());
        return boxed == typeInfo;
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
            return typeInfo.isPrivateNested();
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
        assert other != null;

        if (equals(other)) return this;

        TypeInfo bestType = bestTypeInfo();
        TypeInfo otherBestType = other.bestTypeInfo();
        boolean isPrimitive = Primitives.isPrimitiveExcludingVoid(this) || bestType != null && Primitives.isBoxedExcludingVoid(bestType);
        boolean otherIsPrimitive = Primitives.isPrimitiveExcludingVoid(other) || otherBestType != null && Primitives.isBoxedExcludingVoid(otherBestType);
        if (isPrimitive && otherIsPrimitive) {
            return inspectionProvider.getPrimitives().widestType(this, other);
        }
        if (isPrimitive && other == ParameterizedType.NULL_CONSTANT) {
            return inspectionProvider.getPrimitives().boxed(bestType).asParameterizedType(inspectionProvider);
        }
        if (otherIsPrimitive && this == ParameterizedType.NULL_CONSTANT) {
            return inspectionProvider.getPrimitives().boxed(otherBestType).asParameterizedType(inspectionProvider);
        }
        if (isPrimitive || otherIsPrimitive) return inspectionProvider.getPrimitives().objectParameterizedType; // no common type

        if(other == ParameterizedType.NULL_CONSTANT) return this;
        if(this == ParameterizedType.NULL_CONSTANT) return other;

        if (bestType == null || otherBestType == null) return inspectionProvider.getPrimitives().objectParameterizedType; // no common type
        if (isAssignableFrom(inspectionProvider, other)) {
            return this;
        }
        if (other.isAssignableFrom(inspectionProvider, this)) {
            return other;
        }
        return inspectionProvider.getPrimitives().objectParameterizedType; // no common type
    }

    public Boolean isImplicitlyOrAtLeastEventuallyE2Immutable(AnalysisProvider analysisProvider, TypeInfo typeBeingAnalysed) {
        if (arrays > 0) return false;
        Boolean immu = isAtLeastEventuallyE2Immutable(analysisProvider);
        if (immu == Boolean.TRUE) return true;
        Boolean implicit = isImplicitlyImmutable(analysisProvider, typeBeingAnalysed);
        if (implicit == Boolean.TRUE) return true;
        return immu == null || implicit == null ? null : false;
    }

    public Boolean isImplicitlyImmutable(AnalysisProvider analysisProvider, TypeInfo typeBeingAnalysed) {
        TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(typeBeingAnalysed);
        Set<ParameterizedType> implicitTypes = typeAnalysis.getImplicitlyImmutableDataTypes();
        return implicitTypes == null ? null : implicitTypes.contains(this);
    }

    public Boolean isAtLeastEventuallyE2Immutable(AnalysisProvider analysisProvider) {
        TypeInfo bestType = bestTypeInfo();
        if (bestType == null) return false;
        int immutable = analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
        if (immutable == Level.DELAY) return null;
        return MultiLevel.isAtLeastEventuallyE2Immutable(immutable);
    }

    public Boolean isE2Immutable(AnalysisProvider analysisProvider) {
        TypeInfo bestType = bestTypeInfo();
        if (bestType == null) return false;
        int immutable = analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
        if (immutable == Level.DELAY) return null;
        return immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE; // exactly
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

    public int defaultNotNull() {
        return Primitives.isPrimitiveExcludingVoid(this) ? MultiLevel.EFFECTIVELY_NOT_NULL : MultiLevel.NULLABLE;
    }

    public int defaultIndependent() {
        return Primitives.isPrimitiveExcludingVoid(this) ? MultiLevel.INDEPENDENT: MultiLevel.DEPENDENT;
    }

    public int defaultImmutable(AnalyserContext analyserContext) {
        TypeInfo bestType = bestTypeInfo();
        if(bestType == null) return MultiLevel.NOT_INVOLVED;
        if(Primitives.isPrimitiveExcludingVoid(bestType)) return MultiLevel.EFFECTIVELY_E2IMMUTABLE;
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(bestType);
        return typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
    }
}
