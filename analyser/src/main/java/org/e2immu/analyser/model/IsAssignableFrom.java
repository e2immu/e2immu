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

import org.e2immu.analyser.model.expression.CharConstant;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.IntConstant;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

/**
 * The default assignment mode is COVARIANT, in that you can always assign a sub-type to a super-type, as in Number <- Integer,
 * or Object <- String.
 *
 * @param inspectionProvider to obtain type inspections
 * @param target             the type as in the assignment "target = from"
 * @param from               the type as in the assignment "target = from"
 */
public record IsAssignableFrom(InspectionProvider inspectionProvider,
                               ParameterizedType target,
                               ParameterizedType from) {

    public IsAssignableFrom {
        Objects.requireNonNull(inspectionProvider);
        Objects.requireNonNull(target);
        Objects.requireNonNull(from);
    }

    public static final int NOT_ASSIGNABLE = -1;

    public boolean execute() {
        return execute(false, Mode.COVARIANT) != NOT_ASSIGNABLE;
    }

    private static final IntBinaryOperator REDUCER = (a, b) -> a == NOT_ASSIGNABLE || b == NOT_ASSIGNABLE ? NOT_ASSIGNABLE : a + b;

    public static final int EQUALS = 0;
    private static final int ASSIGN_TO_NULL = 0;
    public static final int SAME_UNDERLYING_TYPE = 1;
    private static final int BOXING_TO_PRIMITIVE = 1;
    private static final int BOXING_FROM_PRIMITIVE = 1;
    public static final int IN_HIERARCHY = 10;
    private static final int UNBOUND_WILDCARD = 100;

    public enum Mode {
        INVARIANT, // everything has to be identical, there is no leeway with respect to hierarchy
        COVARIANT, // allow assignment of sub-types: Number <-- Integer; List<Integer> <-- IntegerList
        CONTRAVARIANT, // allow for super-types:  Integer <-- Number; IntegerList <-- List<Integer>
        ANY, // accept everything
        COVARIANT_ERASURE, // covariant, but ignore all type parameters
    }

    /**
     * @param ignoreArrays do the comparison, ignoring array information
     * @param mode         the comparison mode
     * @return a numeric "nearness", the lower, the better and the more specific
     */

    public int execute(boolean ignoreArrays, Mode mode) {
        if (target == from || target.equals(from) || ignoreArrays && target.equalsIgnoreArrays(from)) return EQUALS;

        // NULL
        if (from == ParameterizedType.NULL_CONSTANT) {
            if (Primitives.isPrimitiveExcludingVoid(target)) return NOT_ASSIGNABLE;
            return ASSIGN_TO_NULL;
        }

        // Assignment to Object: everything can be assigned to object!
        if (ignoreArrays) {
            if (target.typeInfo != null && Primitives.isJavaLangObject(target.typeInfo)) {
                return IN_HIERARCHY;
            }
        } else if (Primitives.isJavaLangObject(target)) {
            return IN_HIERARCHY;
        }


        // TWO TYPES, POTENTIALLY WITH PARAMETERS, but not TYPE PARAMETERS
        // List<T> vs LinkedList; int vs double, but not T vs LinkedList
        if (target.typeInfo != null && from.typeInfo != null) {

            // arrays?
            if (!ignoreArrays) {
                if (target.arrays != from.arrays) {
                    return NOT_ASSIGNABLE;
                }
                if (target.arrays > 0) {
                    // recurse without the arrays; target and from remain the same
                    return execute(true, Mode.COVARIANT);
                }
            }

            // PRIMITIVES
            if (Primitives.isPrimitiveExcludingVoid(from)) {
                if (Primitives.isPrimitiveExcludingVoid(target)) {
                    // use a dedicated method in Primitives
                    return inspectionProvider.getPrimitives().isAssignableFromTo(from, target,
                            mode == Mode.COVARIANT || mode == Mode.COVARIANT_ERASURE);
                }
                return checkBoxing(target.typeInfo, from.typeInfo) ? BOXING_FROM_PRIMITIVE : NOT_ASSIGNABLE;
            }
            if (Primitives.isPrimitiveExcludingVoid(target)) {
                // the other one is not a primitive
                return checkBoxing(from.typeInfo, target.typeInfo) ? BOXING_TO_PRIMITIVE : NOT_ASSIGNABLE;
            }

            // two different types, so they must be in a hierarchy
            if (target.typeInfo != from.typeInfo) {
                return differentNonNullTypeInfo(mode);
            }
            // identical base type, so look at type parameters
            return sameNoNullTypeInfo(mode);

        }

        if (target.typeInfo != null && from.typeParameter != null) {
            List<ParameterizedType> otherTypeBounds = from.typeParameter.getTypeBounds();
            if (otherTypeBounds.isEmpty()) {
                return Primitives.isJavaLangObject(target.typeInfo) ? IN_HIERARCHY : NOT_ASSIGNABLE;
            }
            return otherTypeBounds.stream().mapToInt(bound -> new IsAssignableFrom(inspectionProvider, target, bound)
                            .execute(true, mode))
                    .min().orElseThrow();
        }

        // I am a type parameter
        if (target.typeParameter != null) {
            return targetIsATypeParameter(mode);
        }
        // if wildcard is unbound, I am <?>; anything goes
        return target.wildCard == ParameterizedType.WildCard.UNBOUND ? UNBOUND_WILDCARD : NOT_ASSIGNABLE;
    }

    private int targetIsATypeParameter(Mode mode) {
        assert target.typeParameter != null;

        List<ParameterizedType> targetTypeBounds = target.typeParameter.getTypeBounds();
        if (targetTypeBounds.isEmpty()) {
            return IN_HIERARCHY;
        }
        // other is a type
        if (from.typeInfo != null) {
            return targetTypeBounds.stream().mapToInt(bound -> new IsAssignableFrom(inspectionProvider, bound, target)
                            .execute(true, mode))
                    .min().orElseThrow();
        }
        if (from.typeParameter != null) {
            List<ParameterizedType> fromTypeBounds = from.typeParameter.getTypeBounds();
            if (fromTypeBounds.isEmpty()) {
                return IN_HIERARCHY;
            }
            // we both have type bounds; we go for the best combination
            int min = Integer.MAX_VALUE;
            for (ParameterizedType myBound : targetTypeBounds) {
                for (ParameterizedType otherBound : fromTypeBounds) {
                    int value = new IsAssignableFrom(inspectionProvider, myBound, otherBound)
                            .execute(true, mode);
                    if (value < min) min = value;
                }
            }
            return min;

        }
        return NOT_ASSIGNABLE;
    }

    private int sameNoNullTypeInfo(Mode mode) {
        if (mode == Mode.COVARIANT_ERASURE) return SAME_UNDERLYING_TYPE;

        // List<E> <-- List<String>
        if (target.parameters.isEmpty()) {
            // ? extends Type <-- Type ; Type <- ? super Type; ...
            if (compatibleWildcards(mode, target.wildCard, from.wildCard)) {
                return SAME_UNDERLYING_TYPE;
            }
            return NOT_ASSIGNABLE;
        }
        return ListUtil.joinLists(target.parameters, from.parameters)
                .mapToInt(p -> {
                    Mode newMode = mode == Mode.INVARIANT ? Mode.INVARIANT :
                            switch (p.k.wildCard) {
                                case EXTENDS -> mode == Mode.COVARIANT ? Mode.COVARIANT : Mode.CONTRAVARIANT;
                                case SUPER -> mode == Mode.COVARIANT ? Mode.CONTRAVARIANT : Mode.COVARIANT;
                                case NONE -> Mode.INVARIANT;
                                case UNBOUND -> Mode.ANY;
                            };
                    return new IsAssignableFrom(inspectionProvider, p.k, p.v).execute(true, newMode);
                }).reduce(0, REDUCER);
    }

    private int differentNonNullTypeInfo(Mode mode) {
        if (from.isFunctionalInterface(inspectionProvider) && target.isFunctionalInterface(inspectionProvider)) {
            // two functional interfaces, yet different TypeInfo objects
            return functionalInterface(mode);
        }
        return switch (mode) {
            case COVARIANT, COVARIANT_ERASURE -> hierarchy(target, from, mode);
            case CONTRAVARIANT -> hierarchy(from, target, mode);
            case INVARIANT -> NOT_ASSIGNABLE;
            case ANY -> throw new UnsupportedOperationException("?");
        };
    }

    /*
    either COVARIANT_ERASURE, which means we simply have to test the number of parameters and isVoid,
    or INVARIANT... all type parameters identical
     */
    private int functionalInterface(Mode mode) {
        MethodInspection mTarget = target.findSingleAbstractMethodOfInterface(inspectionProvider).methodInspection;
        MethodInspection mFrom = from.findSingleAbstractMethodOfInterface(inspectionProvider).methodInspection;
        if (mTarget.getParameters().size() != mFrom.getParameters().size()) return NOT_ASSIGNABLE;
        boolean targetIsVoid = Primitives.isVoid(mTarget.getReturnType());
        boolean fromIsVoid = Primitives.isVoid(mFrom.getReturnType());
        // target void -> fromIsVoid is unimportant, we can assign a function to a consumer
        if (!targetIsVoid && fromIsVoid) return NOT_ASSIGNABLE;

        if (mode == Mode.COVARIANT_ERASURE) return SAME_UNDERLYING_TYPE;
        // now, ensure that all type parameters have equal values
        int i = 0;
        for (ParameterInfo t : mTarget.getParameters()) {
            ParameterInfo f = mFrom.getParameters().get(i);
            if (!t.parameterizedType.equals(f.parameterizedType)) return NOT_ASSIGNABLE;
            i++;
        }
        if (!mTarget.getReturnType().equals(mFrom.getReturnType())) return NOT_ASSIGNABLE;
        return EQUALS;
    }

    private int hierarchy(ParameterizedType target, ParameterizedType from, Mode mode) {
        TypeInspection otherTypeInspection = inspectionProvider.getTypeInspection(from.typeInfo);
        for (ParameterizedType interfaceImplemented : otherTypeInspection.interfacesImplemented()) {
            ParameterizedType concreteType = from.concreteDirectSuperType(inspectionProvider, interfaceImplemented);
            int scoreInterface = new IsAssignableFrom(inspectionProvider, target, concreteType)
                    .execute(true, mode);
            if (scoreInterface != NOT_ASSIGNABLE) return IN_HIERARCHY + scoreInterface;
        }
        ParameterizedType parentClass = otherTypeInspection.parentClass();
        if (parentClass != null && !Primitives.isJavaLangObject(parentClass)) {
            ParameterizedType concreteType = from.concreteDirectSuperType(inspectionProvider, parentClass);
            int scoreParent = new IsAssignableFrom(inspectionProvider, target, concreteType)
                    .execute(true, mode);
            if (scoreParent != NOT_ASSIGNABLE) return IN_HIERARCHY + scoreParent;
        }
        return NOT_ASSIGNABLE;
    }

    private boolean compatibleWildcards(Mode mode, ParameterizedType.WildCard w1, ParameterizedType.WildCard w2) {
        if (w1 == w2) return true;
        return mode != Mode.INVARIANT || w1 != ParameterizedType.WildCard.NONE;
    }

    private boolean checkBoxing(TypeInfo targetInfo, TypeInfo fromPrimitiveType) {
        TypeInfo boxed = fromPrimitiveType.asParameterizedType(inspectionProvider).toBoxed(inspectionProvider.getPrimitives());
        return boxed == targetInfo;
    }
}
