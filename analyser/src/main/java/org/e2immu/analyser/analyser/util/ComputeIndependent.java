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

public record ComputeIndependent(AnalyserContext analyserContext, SetOfTypes hiddenContentOfCurrentType) {

    public ComputeIndependent {
        assert analyserContext != null;
        assert hiddenContentOfCurrentType != null;
    }

    /**
     * Variables of two types are linked to each other, at a given <code>linkLevel</code>.
     * Assuming that one of them is a field, and the other a parameter, or a return value,
     * return the value of the INDEPENDENT property given this linking.
     * <p>
     * The types can be identical, assignable to each other, or completely different.
     * In the latter case, they may share a common subtype.
     *
     * @param linkLevel  any of STATICALLY_ASSIGNED, ASSIGNED, DEPENDENT, INDEPENDENT1, NO
     * @param a          one of the two types
     * @param immutableA not null if you already know the immutable value of <code>a</code>
     * @param b          one of the two types, can be equal to <code>a</code>
     * @return the value for the INDEPENDENT property
     */
    public DV compute(DV linkLevel, ParameterizedType a, DV immutableA, ParameterizedType b) {
        if (LinkedVariables.LINK_INDEPENDENT.equals(linkLevel)) return MultiLevel.INDEPENDENT_DV;

        // when not null, the types are identical or assignable to each other
        ParameterizedType oneType;
        // when not null, the types are identical or assignable to each other
        DV immutableOneType;
        boolean aUnboundTypeParameter = a.isUnboundTypeParameter();
        boolean bUnboundTypeParameter = b.isUnboundTypeParameter();

        if (a.equals(b)) {
            oneType = a;
            immutableOneType = immutableA;
        } else if (!aUnboundTypeParameter && a.isAssignableFrom(analyserContext, b)) {
            oneType = a;
            immutableOneType = immutableA;
        } else if (!bUnboundTypeParameter && b.isAssignableFrom(analyserContext, a)) {
            oneType = b;
            immutableOneType = null;
        } else {
            oneType = null;
            immutableOneType = null;
        }

        if (LINK_STATICALLY_ASSIGNED.equals(linkLevel) || LINK_ASSIGNED.equals(linkLevel)) {
            // types a and b are either equal or assignable, otherwise one cannot assign
            assert oneType != null : "Assignment?";

            DV immutable = immutableOneType != null ? immutableOneType : analyserContext.typeImmutable(oneType);
            return MultiLevel.independentCorrespondingToImmutable(immutable);
        }

        if (oneType != null) {
            if (LINK_DEPENDENT.equals(linkLevel)) {
                return MultiLevel.DEPENDENT_DV;
            }
            if (LINK_INDEPENDENT_HC.equals(linkLevel)) {
                // e.g. set1.addAll(set2) -- content of set2 added to set1, same type
                // result is INDEPENDENT_HC unless the common type is recursively immutable
                TypeInfo bestTypeInfo = oneType.bestTypeInfo();
                assert bestTypeInfo != null;
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(bestTypeInfo);
                SetOfTypes hiddenContent = typeAnalysis.getHiddenContentTypes();
                assert !hiddenContent.isEmpty();

                DV determinedByTypeParameters = typeAnalysis.immutableDeterminedByTypeParameters();
                if (determinedByTypeParameters.isDelayed()) return determinedByTypeParameters;
                if (determinedByTypeParameters.valueIsTrue()) {
                    DV immutable = analyserContext.immutableOfHiddenContentInTypeParameters(oneType);
                    return MultiLevel.independentCorrespondingToImmutable(immutable);
                }
                return MultiLevel.INDEPENDENT_HC_DV;
            }
            throw new UnsupportedOperationException();
        }
        // now 2 different, non-assignable types remain...
        if (aUnboundTypeParameter && bUnboundTypeParameter) {
            return MultiLevel.INDEPENDENT_DV;
        }
        if (aUnboundTypeParameter) {
            return verifyIncludedInHiddenContentOf(immutableA, linkLevel, a, b, MultiLevel.INDEPENDENT_DV);
        }
        if (bUnboundTypeParameter) {
            return verifyIncludedInHiddenContentOf(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV,
                    linkLevel, b, a, MultiLevel.INDEPENDENT_DV);
        }
        DV aInB = verifyIncludedInHiddenContentOf(immutableA, linkLevel, a, b, null);
        if (aInB != null) return aInB;
        DV bInA = verifyIncludedInHiddenContentOf(null, linkLevel, b, a, null);
        if (bInA != null) return bInA;

        return intersection(linkLevel, a, b);
    }

    private DV intersection(DV linkLevel, ParameterizedType a, ParameterizedType b) {
        if (LINK_DEPENDENT.equals(linkLevel)) return MultiLevel.DEPENDENT_DV;

        TypeAnalysis ta = analyserContext.getTypeAnalysisNullWhenAbsent(a.bestTypeInfo());
        TypeAnalysis tb = analyserContext.getTypeAnalysisNullWhenAbsent(b.bestTypeInfo());
        if (ta == null || tb == null) return MultiLevel.INDEPENDENT_DV;
        CausesOfDelay causes = ta.hiddenContentAndExplicitTypeComputationDelays().causesOfDelay()
                .merge(tb.hiddenContentAndExplicitTypeComputationDelays().causesOfDelay());
        if (causes.isDelayed()) return causes;
        SetOfTypes hiddenA = ta.getHiddenContentTypes(a);
        SetOfTypes hiddenB = tb.getHiddenContentTypes(b);
        SetOfTypes intersection = hiddenA.intersection(hiddenB);

        if (intersection.isEmpty()) return MultiLevel.INDEPENDENT_DV;

        DV inHiddenContent = intersection.types().stream()
                .filter(hiddenContentOfCurrentType::contains)
                .map(pt -> {
                    DV immutable = analyserContext.typeImmutable(pt);
                    return MultiLevel.independentCorrespondingToImmutable(immutable);
                }).reduce(MultiLevel.INDEPENDENT_DV, DV::min);
        if (MultiLevel.INDEPENDENT_DV == inHiddenContent) {
            return MultiLevel.INDEPENDENT_HC_DV;
        }
        return MultiLevel.independentCorrespondingToImmutable(inHiddenContent);
    }

    private DV verifyIncludedInHiddenContentOf(DV immutableA, DV linkLevel, ParameterizedType a, ParameterizedType b, DV onFail) {
        TypeInfo typeInfo = b.bestTypeInfo();
        if (typeInfo == null) {
            // T and T[], for example
            assert a.arrays != b.arrays;
            return MultiLevel.INDEPENDENT_HC_DV;
        }
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(typeInfo);
        if (typeAnalysis == null) return MultiLevel.INDEPENDENT_DV;
        CausesOfDelay causes = typeAnalysis.hiddenContentAndExplicitTypeComputationDelays();
        if (causes.isDelayed()) {
            return causes;
        }
        SetOfTypes hiddenB = typeAnalysis.getHiddenContentTypes(b);
        if (hiddenB.contains(a)) {
            if (LINK_DEPENDENT.equals(linkLevel)) return MultiLevel.DEPENDENT_DV;

            /*
            but what if 'a' is not part of the hidden content of the current type? then we should return the
            independent value ~ immutableA.
             */
            if (!hiddenContentOfCurrentType.contains(a)) {
                DV immutable = immutableA == null ? analyserContext.typeImmutable(a) : immutableA;
                return MultiLevel.independentCorrespondingToImmutable(immutable);
            }
            return MultiLevel.INDEPENDENT_HC_DV; // even if "a" is mutable!!
        }
        return onFail;
    }
}
