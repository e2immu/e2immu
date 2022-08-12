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

public class ComputeIndependent {

    private final AnalyserContext analyserContext;
    private final TypeInfo currentType;

    public ComputeIndependent(AnalyserContext analyserContext, TypeInfo currentType) {
        this.analyserContext = analyserContext;
        this.currentType = currentType;
    }

    /**
     * @param linkLevel any of STATICALLY_ASSIGNED, ASSIGNED, DEPENDENT, INDEPENDENT1, NO
     * @param a         one of the two types
     * @param b         one of the two types, can be equal to a
     * @return the value for the INDEPENDENT property
     */
    public DV compute(DV linkLevel, ParameterizedType a, DV immutableA, ParameterizedType b) {
        if (LinkedVariables.LINK_NONE.equals(linkLevel)) return MultiLevel.INDEPENDENT_DV;

        ParameterizedType oneType;
        DV immutableOneType;
        boolean aUnboundTypeParameter = a.isUnboundTypeParameter();
        boolean bUnboundTypeParameter = b.isUnboundTypeParameter();

        if (a == b || a.equals(b)) {
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
            // types a and b are either equal or assignable
            assert oneType != null : "Assignment?";

            // the 'defaultImmutable' call contains the transparent check
            DV immutable = immutableOneType != null ? immutableOneType
                    : analyserContext.defaultImmutable(oneType, false, currentType);
            if (immutable.isDelayed()) return immutable;
            int immutableLevel = MultiLevel.level(immutable);
            return MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
        }

        if (oneType != null) {
            if (LINK_DEPENDENT.equals(linkLevel)) {
                return MultiLevel.DEPENDENT_DV;
            }
            if (LINK_INDEPENDENT1.equals(linkLevel)) {
                // e.g. set1.addAll(set2) -- content of set2 added to set1, same type
                // result is INDEPENDENT1 unless the common type is recursively immutable
                TypeInfo bestTypeInfo = oneType.bestTypeInfo();
                assert bestTypeInfo != null;
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(bestTypeInfo);
                SetOfTypes hiddenContent = typeAnalysis.getHiddenContentTypes();
                assert !hiddenContent.isEmpty();

                DV canIncrease = typeAnalysis.immutableCanBeIncreasedByTypeParameters();
                if (canIncrease.isDelayed()) return canIncrease;
                if (canIncrease.valueIsTrue()) {
                    DV immutable = analyserContext.immutableOfHiddenContentInTypeParameters(oneType, currentType);
                    if (immutable.isDelayed()) return immutable;
                    if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
                        return MultiLevel.INDEPENDENT_DV;
                    }
                }
                return MultiLevel.INDEPENDENT_1_DV;
            }
            throw new UnsupportedOperationException();
        }
        // now 2 different, non-assignable types remain...
        if (aUnboundTypeParameter && bUnboundTypeParameter) {
            return MultiLevel.INDEPENDENT_DV;
        }
        if (aUnboundTypeParameter) {
            return verifyIncludedInHiddenContentOf(linkLevel, a, b, MultiLevel.INDEPENDENT_DV);
        }
        if (bUnboundTypeParameter) {
            return verifyIncludedInHiddenContentOf(linkLevel, b, a, MultiLevel.INDEPENDENT_DV);
        }
        DV aInB = verifyIncludedInHiddenContentOf(linkLevel, a, b, null);
        if (aInB != null) return aInB;
        DV bInA = verifyIncludedInHiddenContentOf(linkLevel, b, a, null);
        if (bInA != null) return bInA;

        return intersection(linkLevel, a, b);
    }

    private DV intersection(DV linkLevel, ParameterizedType a, ParameterizedType b) {
        if (LINK_DEPENDENT.equals(linkLevel)) return MultiLevel.DEPENDENT_DV;

        TypeAnalysis ta = analyserContext.getTypeAnalysisNullWhenAbsent(a.bestTypeInfo());
        TypeAnalysis tb = analyserContext.getTypeAnalysisNullWhenAbsent(b.bestTypeInfo());
        if (ta == null || tb == null) return MultiLevel.INDEPENDENT_DV;
        CausesOfDelay causes = ta.transparentAndExplicitTypeComputationDelays().causesOfDelay()
                .merge(tb.transparentAndExplicitTypeComputationDelays().causesOfDelay());
        if (causes.isDelayed()) return causes;
        SetOfTypes hiddenA = ta.getHiddenContentTypes(a);
        SetOfTypes hiddenB = tb.getHiddenContentTypes(b);
        SetOfTypes intersection = hiddenA.intersection(hiddenB);
        if (intersection.isEmpty()) return MultiLevel.INDEPENDENT_DV;
        return MultiLevel.INDEPENDENT_1_DV;
    }

    private DV verifyIncludedInHiddenContentOf(DV linkLevel, ParameterizedType a, ParameterizedType b, DV onFail) {
        TypeInfo typeInfo = b.bestTypeInfo();
        if (typeInfo == null) {
            // T and T[], for example
            assert a.arrays != b.arrays;
            return MultiLevel.INDEPENDENT_1_DV;
        }
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(typeInfo);
        if (typeAnalysis == null) return MultiLevel.INDEPENDENT_DV;
        CausesOfDelay causes = typeAnalysis.transparentAndExplicitTypeComputationDelays();
        if (causes.isDelayed()) {
            return causes;
        }
        SetOfTypes hiddenB = typeAnalysis.getHiddenContentTypes(b);
        if (hiddenB.contains(a)) {
            if (LINK_DEPENDENT.equals(linkLevel)) return MultiLevel.DEPENDENT_DV;
            return MultiLevel.INDEPENDENT_1_DV; // even if "a" is mutable!!
        }
        return onFail;
    }
}
