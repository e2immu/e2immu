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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;

import static org.e2immu.analyser.analyser.LinkedVariables.*;

public record ComputeIndependent(AnalyserContext analyserContext,
                                 SetOfTypes hiddenContentOfCurrentType,
                                 TypeInfo currentPrimaryType) {

    public ComputeIndependent {
        assert analyserContext != null;
        assert currentPrimaryType != null;
    }

    public ComputeIndependent(AnalyserContext analyserContext, TypeInfo currentPrimaryType) {
        this(analyserContext, null, currentPrimaryType);
    }

    /**
     * Variables of two types are linked to each other, at a given <code>linkLevel</code>.
     * <p>
     * Case 1: {@link org.e2immu.analyser.analyser.impl.ComputedParameterAnalyser}
     * The first type ('a') is the parameter's (formal) type, the second ('b') is a field.
     * <p>
     * Case 2: {@link org.e2immu.analyser.analyser.impl.ComputingMethodAnalyser}
     * The first type ('a') is the concrete return type, the second ('b') is a field
     * <p>
     * Case 3: {@link org.e2immu.analyser.analyser.impl.FieldAnalyserImpl}
     * The first type ('a') is the field's (formal? concrete?) type
     * <p>
     * The link level has not been adjusted to the mutability of the inclusion or intersection type:
     * if a mutable X is part of the hidden content of Set, we use <code>IN_HC_OF</code>, rather than <code>DEPENDENT</code>.
     * On the other hand, a link level of DEPENDENT assumes that the underlying type is mutable.
     * A link level of INDEPENDENT should not occur, but for convenience we translate directly. The immutability status
     * of the types is not important in this case.
     * <p>
     * LinkedVariables(target) = { field1:linkLevel1, field2:linkLevel2, ...}
     *
     * @param linkLevel       any of STATICALLY_ASSIGNED, ASSIGNED, DEPENDENT, IN_HC_OF, COMMON_HC, INDEPENDENT
     * @param a               one of the two types
     * @param immutableAInput not null if you already know the immutable value of <code>a</code>
     * @param b               one of the two types, can be equal to <code>a</code>
     * @return the value for the INDEPENDENT property
     */
    public DV typesAtLinkLevel(DV linkLevel, ParameterizedType a, DV immutableAInput, ParameterizedType b) {
        assert linkLevel.isDone();
        if (LINK_STATICALLY_ASSIGNED.equals(linkLevel) || LINK_ASSIGNED.equals(linkLevel)) {
            /*
             Types a and b are either equal or assignable, otherwise one cannot assign.
             The type relation cannot be IN_HC_OF, it should be COMMON_HC (or INDEPENDENT in case of no information).
             We return the independent value corresponding to the most specific type.
             */
            DV immutableA = immutableAInput == null ? analyserContext.typeImmutable(a) : immutableAInput;
            DV immutableB = analyserContext.typeImmutable(b);
            DV immutable = max(immutableA, immutableB);
            return MultiLevel.independentCorrespondingToImmutable(immutable);
        }
        if (LINK_INDEPENDENT.equals(linkLevel)) return MultiLevel.INDEPENDENT_DV;
        if (LINK_DEPENDENT.equals(linkLevel)) return MultiLevel.DEPENDENT_DV;

        assert LINK_IS_HC_OF.equals(linkLevel) || LINK_COMMON_HC.equals(linkLevel);
        DV immutableOfIntersection = immutableOfIntersectionOfHiddenContent(a, b);
        return MultiLevel.independentCorrespondingToImmutable(immutableOfIntersection);
    }

    private DV max(DV immutableA, DV immutableB) {
        // minor shortcut
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableA)
                || MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableB)) {
            return MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
        }
        return immutableA.max(immutableB);
    }

    private DV immutableOfIntersectionOfHiddenContent(ParameterizedType pt1, ParameterizedType pt2) {
        if (pt1.equals(pt2) && pt1.arrays > 0) {
            /*
             Independent1_1, Set<T> vs Set<T>, we want the immutable value of T, not of Set; this rule does NOT apply
             DependentVariables_2, X[] and X[], we want the immutable value of X; this rule does apply
             */
            return analyserContext.typeImmutable(pt1.copyWithoutArrays());
        }

        TypeInfo b1 = pt1.bestTypeInfo();
        TypeInfo b2 = pt2.bestTypeInfo();
        if (b1 == null && b2 == null) {
            // two unbound type parameters
            assert pt1.typeParameter != null && pt1.typeParameter.equals(pt2.typeParameter);
            return MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV;
        }
        if (b2 != null) {
            if (b2.equals(b1) && pt1.arrays != pt2.arrays) {
                return analyserContext.typeImmutable(pt1.copyWithoutArrays());
            }
            DV dv = immutableSmallInsideBig(pt1, pt2, b2);
            if (dv != null) {
                return dv;
            }
        }
        if (b1 != null) {
            DV dv = immutableSmallInsideBig(pt2, pt1, b1);
            if (dv != null) {
                return dv;
            }
        }
        assert b1 != null && b2 != null : "Should have been picked up in one of the previous cases";

        // intersection

        TypeAnalysis t1 = analyserContext.getTypeAnalysisNullWhenAbsent(b1);
        TypeAnalysis t2 = analyserContext.getTypeAnalysisNullWhenAbsent(b2);
        if (t1 == null || t2 == null) {
            // no info
            return MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
        }

        CausesOfDelay causes = t1.hiddenContentDelays().causesOfDelay().merge(t2.hiddenContentDelays().causesOfDelay());
        if (causes.isDelayed()) {
            // delay
            return causes;
        }
        SetOfTypes intersection = analyserContext.intersectionOfHiddenContent(pt1, t1, pt2, t2);

        if (intersection.isEmpty()) {
            // no intersection between
            return MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
        }

        DV min = MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
        for (ParameterizedType pt : intersection.types()) {
            DV immutable = analyserContext.typeImmutable(pt);
            if (MultiLevel.isMutable(immutable)) return MultiLevel.MUTABLE_DV;
            if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {// IMPROVE should we filter? && hiddenContentOfCurrentType.contains(pt)) {
                min = MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV;
            }
        }
        return min;
    }

    /*
     Type 1 is part of hidden content of type 2

     EXAMPLE 1: list.add(x), with E the formal type pt1, and X the type x; we return immutable of X
     EXAMPLE 2: list.addAll(coll) -> null, not for us
     */
    private DV immutableSmallInsideBig(ParameterizedType small, ParameterizedType big, TypeInfo typeInfoBig) {
        TypeAnalysis typeAnalysisBig = analyserContext.getTypeAnalysisNullWhenAbsent(typeInfoBig);
        if (typeAnalysisBig == null) {
            // no info
            return MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
        }
        if (typeAnalysisBig.hiddenContentDelays().isDelayed()) {
            // delay
            return typeAnalysisBig.hiddenContentDelays();
        }
        SetOfTypes translatedHc = typeAnalysisBig.getHiddenContentTypes().translate(analyserContext, big);
        if (translatedHc.contains(small)) {
            return analyserContext.typeImmutable(small);
        }
        SetOfTypes translatedHcWithoutArrays = translatedHc.dropArrays();
        if (translatedHcWithoutArrays.contains(small)) {
            // symmetric analogue of the next one, HC = HasSize[], small = HasSize
            return analyserContext.typeImmutable(small);
        }
        ParameterizedType smallWithoutArrays = small.copyWithoutArrays();
        if (translatedHc.contains(smallWithoutArrays)) {
            // HC = HasSize, small = HasSize[]
            return analyserContext.typeImmutable(smallWithoutArrays);
        }
        return null; // try something else
    }

    // ***********************************************************************************************************


    /**
     * Is the first type part of the hidden content of the second?
     *
     * @param pt1 the first type
     * @param pt2 the second type
     * @return either IS_HC_OF (answer: yes), COMMON_HC (answer: no), or INDEPENDENT (a type happens to be
     * recursively immutable), or a delay when we're waiting for immutable
     */
    public DV directedLinkLevelOfTwoHCRelatedTypes(ParameterizedType pt1, ParameterizedType pt2) {
        TypeInfo t2 = pt2.bestTypeInfo();
        if (t2 != null) {
            DV immutable = immutableSmallInsideBig(pt1, pt2, t2);
            if (immutable != null) {
                if (immutable.isDelayed()) return immutable;
                if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) return LINK_INDEPENDENT;
                return LINK_IS_HC_OF; // mutable, immutable with hidden content
            }
        }
        return LINK_COMMON_HC;
    }


    /**
     * How do the types relate given that there is a hidden content relation?
     *
     * @param pt1 the first type
     * @param pt2 the second type, there is no order, you can exchange pt1 and pt2
     * @return either DEPENDENT, IS_HC_OF, COMMON_HC, or INDEPENDENT, or a delay when we're waiting for immutable
     */
    public DV linkLevelOfTwoHCRelatedTypes(ParameterizedType pt1, ParameterizedType pt2) {
        if (pt1.equals(pt2) && pt1.arrays > 0) {
            return LINK_COMMON_HC;
        }

        TypeInfo b1 = pt1.bestTypeInfo();
        TypeInfo b2 = pt2.bestTypeInfo();

        if (b1 == null && b2 == null) {
            // two unbound type parameters
            if (pt1.arrays == 0 && pt2.arrays > 0 || pt1.arrays > 0 && pt2.arrays == 0) {
                return LINK_IS_HC_OF;
            }
            assert pt1.typeParameter != null && pt1.typeParameter.equals(pt2.typeParameter);
            return LinkedVariables.LINK_COMMON_HC;
        }

        if (b2 != null) {
            if (b2.equals(b1)) {
                if (pt1.arrays == 0 && pt2.arrays > 0 || pt1.arrays > 0 && pt2.arrays == 0) {
                    return LINK_IS_HC_OF;
                }
                return LINK_COMMON_HC;
            }

            DV dv = linkLevelSmallInsideBig(pt1, pt2, b2);
            if (dv != null) {
                return dv;
            }
        }
        if (b1 != null) {
            DV dv = linkLevelSmallInsideBig(pt2, pt1, b1);
            if (dv != null) {
                return dv;
            }
        }
        assert b1 != null && b2 != null : "Should have been picked up in one of the previous cases";


        // intersection

        TypeAnalysis t1 = analyserContext.getTypeAnalysisNullWhenAbsent(b1);
        TypeAnalysis t2 = analyserContext.getTypeAnalysisNullWhenAbsent(b2);
        if (t1 == null || t2 == null) return MultiLevel.INDEPENDENT_DV;
        CausesOfDelay causes = t1.hiddenContentDelays().causesOfDelay()
                .merge(t2.hiddenContentDelays().causesOfDelay());
        if (causes.isDelayed()) return causes;
        SetOfTypes intersection = analyserContext.intersectionOfHiddenContent(pt1, t1, pt2, t2);

        if (intersection.isEmpty()) return MultiLevel.INDEPENDENT_DV;

        DV independent = intersection.types().stream()
//                .filter(hiddenContentOfCurrentType::contains) IMPROVE should we do this?
                .map(pt -> {
                    DV immutable = analyserContext.typeImmutable(pt);
                    return MultiLevel.independentCorrespondingToImmutable(immutable);
                }).reduce(MultiLevel.INDEPENDENT_DV, DV::min);
        if (MultiLevel.INDEPENDENT_DV == independent) {
            return LINK_INDEPENDENT;
        }
        if (MultiLevel.DEPENDENT_DV.equals(independent)) {
            return LINK_DEPENDENT;
        }
        return LinkedVariables.LINK_COMMON_HC;
    }

    /*
    list.add(x), with E the formal type pt1, and X the mutable type x;

    the link level
     */
    private DV linkLevelSmallInsideBig(ParameterizedType small, ParameterizedType big, TypeInfo typeInfoBig) {
        DV immutable = immutableSmallInsideBig(small, big, typeInfoBig);
        if (immutable == null) return null;
        if (MultiLevel.isMutable(immutable)) return LINK_DEPENDENT;
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) return LINK_INDEPENDENT;
        return LinkedVariables.LINK_IS_HC_OF;
    }
}
