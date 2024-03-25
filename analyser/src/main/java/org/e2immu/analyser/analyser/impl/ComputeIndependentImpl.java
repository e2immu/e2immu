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

package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.LV.*;
import static org.e2immu.analyser.analyser.LV.CS_ALL;

public record ComputeIndependentImpl(AnalyserContext analyserContext,
                                     SetOfTypes hiddenContentOfCurrentType,
                                     TypeInfo currentType,
                                     boolean myselfIsMutable) implements ComputeIndependent {

    public ComputeIndependentImpl {
        assert analyserContext != null;
        assert currentType != null;
    }

    public ComputeIndependentImpl(AnalyserContext analyserContext, TypeInfo currentPrimaryType) {
        this(analyserContext, null, currentPrimaryType, true);
    }


    @Override
    public LinkedVariables linkedVariables(ParameterizedType sourceType,
                                           LinkedVariables sourceLvs,
                                           DV transferIndependent,
                                           HiddenContentSelector hiddenContentSelectorOfTransfer,
                                           ParameterizedType targetType) {

        // RULE 1: no linking when the source is not linked or there is no transfer
        if (sourceLvs.isEmpty() || MultiLevel.INDEPENDENT_DV.equals(transferIndependent)) {
            return LinkedVariables.EMPTY;
        }
        assert !(hiddenContentSelectorOfTransfer == CS_NONE && transferIndependent.equals(MultiLevel.INDEPENDENT_HC_DV))
                : "Impossible to have no knowledge of hidden content, and INDEPENDENT_HC";

        DV immutableOfSource;
        if (sourceType.isUnboundTypeParameter()) {
            // for the purpose of this algorithm...
            immutableOfSource = MultiLevel.INDEPENDENT_HC_DV;
        } else {
            immutableOfSource = typeImmutable(sourceType);
            // RULE 2: delays
            if (immutableOfSource.isDelayed()) {
                return sourceLvs.changeToDelay(LV.delay(immutableOfSource.causesOfDelay()));
            }

            // RULE 3: immutable -> no link
            if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableOfSource)) {
            /*
             if the result type immutable because of a choice in type parameters, methodIndependent will return
             INDEPENDENT_HC, but the concrete type is deeply immutable
             */
                return LinkedVariables.EMPTY;
            }
        }
        // RULE 4: delays
        if (transferIndependent.isDelayed()) {
            // delay in method independent
            return sourceLvs.changeToDelay(LV.delay(transferIndependent.causesOfDelay()));
        }
        // we'll return a sensible value now

        DV correctedIndependent = targetType == null ? transferIndependent
                : correctIndependent(immutableOfSource, transferIndependent, targetType,
                hiddenContentSelectorOfTransfer);
        HiddenContentSelector correctedTransferSelector = targetType == null ? hiddenContentSelectorOfTransfer
                : correctSelector(hiddenContentSelectorOfTransfer, targetType);
        Map<Variable, LV> newLinked = new HashMap<>();
        CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;
        for (Map.Entry<Variable, LV> e : sourceLvs) {
            DV immutable = typeImmutable(e.getKey().parameterizedType());
            LV lv = e.getValue();
            assert lv.lt(LINK_INDEPENDENT);

             /*
               FIXME check this!
               without the 2nd condition, we get loops of CONTEXT_IMMUTABLE delays, see e.g., Test_Util_07_Trie
                     -> we never delay on this for IMMUTABLE
              */
            if (immutable.isDelayed() && !(e.getKey() instanceof This)) {
                causesOfDelay = causesOfDelay.merge(immutable.causesOfDelay());
            } else {
                if (MultiLevel.isMutable(immutable) && isDependent(transferIndependent, correctedIndependent,
                        immutableOfSource, lv)) {
                    newLinked.put(e.getKey(), LINK_DEPENDENT);
                } else if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
                    LV commonHC;
                    if (lv.isCommonHC()) {
                        commonHC = LV.createHC(correctedTransferSelector, lv.mine());
                    } else {
                        // assigned, dependent...
                        commonHC = LV.createHC(correctedTransferSelector, CS_ALL);
                    }
                    newLinked.put(e.getKey(), commonHC);
                }
            }
        }
        if (causesOfDelay.isDelayed()) {
            return sourceLvs.changeToDelay(LV.delay(causesOfDelay));
        }
        return LinkedVariables.of(newLinked);
    }

    private boolean isDependent(DV transferIndependent, DV correctedIndependent,
                                DV immutableOfSource,
                                LV lv) {
        return
                // situation immutable(mutable), we'll have to override
                MultiLevel.INDEPENDENT_HC_DV.equals(transferIndependent)
                && MultiLevel.DEPENDENT_DV.equals(correctedIndependent)
                ||
                // situation mutable(immutable), dependent method,
                MultiLevel.DEPENDENT_DV.equals(transferIndependent)
                && !lv.isCommonHC()
                && !MultiLevel.isAtLeastImmutableHC(immutableOfSource);
    }

    /*
    Example: Map<K,V>.entrySet() has HCS <0,1>: we keep both type parameters. But Map<Long,V> must have
    only <1>, because type parameter 0 cannot be hidden content in the result.
    Map<StringBuilder, V> is not relevant here, because then the type would be mutable, the corrected independent
    would be "dependent", and we'll not return a commonHC object.
    So we'll only those type parameters that have a recursively immutable instantiation in the concrete type.
     */
    private HiddenContentSelector correctSelector(HiddenContentSelector hiddenContentSelectorOfTransfer,
                                                  ParameterizedType targetType) {
        // find the types corresponding to the hidden content indices
        Map<Integer, ParameterizedType> typesCorrespondingToHC = LV.typesCorrespondingToHC(targetType);
        Set<Integer> selectorSet = ((HiddenContentSelectorImpl) hiddenContentSelectorOfTransfer).set();
        Set<Integer> remaining = typesCorrespondingToHC.entrySet().stream()
                .filter(e -> e.getValue().isTypeParameter() && selectorSet.contains(e.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
        return new LV.HiddenContentSelectorImpl(remaining);
    }

    private DV correctIndependent(DV immutableOfSource,
                                  DV independent,
                                  ParameterizedType targetType,
                                  HiddenContentSelector hiddenContentSelectorOfTransfer) {
        // immutableOfSource is not recursively immutable, independent is not fully independent
        // remaining values immutable: mutable, immutable HC
        // remaining values independent: dependent, independent hc
        if (MultiLevel.DEPENDENT_DV.equals(independent)) {
            if (MultiLevel.isAtLeastImmutableHC(immutableOfSource)) {
                return MultiLevel.INDEPENDENT_HC_DV;
            }
            if (hiddenContentSelectorOfTransfer == CS_ALL) {
                DV immutablePt = typeImmutable(targetType);
                if (immutablePt.isDelayed()) return immutablePt;
                if (MultiLevel.isAtLeastImmutableHC(immutablePt)) {
                    return MultiLevel.INDEPENDENT_HC_DV;
                }
            } else {
                Set<Integer> selectorSet = ((HiddenContentSelectorImpl) hiddenContentSelectorOfTransfer).set();
                Map<Integer, ParameterizedType> typesCorrespondingToHC = LV.typesCorrespondingToHC(targetType);
                boolean allIndependent = true;
                for (Map.Entry<Integer, ParameterizedType> entry : typesCorrespondingToHC.entrySet()) {
                    if (selectorSet.contains(entry.getKey())) {
                        DV immutablePt = typeImmutable(entry.getValue());
                        if (immutablePt.isDelayed()) return immutablePt;
                        if (!MultiLevel.isAtLeastImmutableHC(immutablePt)) {
                            allIndependent = false;
                            break;
                        }
                    }
                }
                if (allIndependent) return MultiLevel.INDEPENDENT_HC_DV;
            }
        }
        if (MultiLevel.INDEPENDENT_HC_DV.equals(independent)) {
            if (hiddenContentSelectorOfTransfer == CS_ALL) {
                DV immutablePt = typeImmutable(targetType);
                if (immutablePt.isDelayed()) return immutablePt;
                if (MultiLevel.isMutable(immutablePt)) {
                    return MultiLevel.DEPENDENT_DV;
                }
            } else {
                Set<Integer> selectorSet = ((HiddenContentSelectorImpl) hiddenContentSelectorOfTransfer).set();
                Map<Integer, ParameterizedType> typesCorrespondingToHC = LV.typesCorrespondingToHC(targetType);
                for (Map.Entry<Integer, ParameterizedType> entry : typesCorrespondingToHC.entrySet()) {
                    if (selectorSet.contains(entry.getKey())) {
                        DV immutablePt = typeImmutable(entry.getValue());
                        if (immutablePt.isDelayed()) return immutablePt;
                        if (MultiLevel.isMutable(immutablePt)) {
                            return MultiLevel.DEPENDENT_DV;
                        }
                    }
                }
            }
        }
        return independent;
    }

    /*
        the value chosen here in case of "isMyself" has an impact, obviously; see e.g. Container_7, E2Immutable_15
         */
    public DV typeImmutable(ParameterizedType pt) {
        if (myselfIsMutable && currentType.isMyself(pt, analyserContext).toFalse(Property.IMMUTABLE)) {
            return MultiLevel.MUTABLE_DV;
        }
        return analyserContext.typeImmutable(pt);
    }

    /**
     * Variables of two types are linked to each other, at a given <code>linkLevel</code>.
     * <p>
     * Case 1: ComputedParameterAnalyser
     * The first type ('a') is the parameter's (formal) type, the second ('b') is a field.
     * <p>
     * Case 2: ComputingMethodAnalyser
     * The first type ('a') is the concrete return type, the second ('b') is a field
     * <p>
     * Case 3: ComputingFieldAnalyser
     * The first type ('a') is the field's (formal? concrete?) type
     * <p>
     * The link level has not been adjusted to the mutability of the inclusion or intersection type:
     * if a mutable X is part of the hidden content of Set, we use <code>DEPENDENT</code>, rather than <code>COMMON_HC</code>.
     * On the other hand, a link level of DEPENDENT assumes that the underlying type is mutable.
     * A link level of INDEPENDENT should not occur, but for convenience we translate directly. The immutability status
     * of the types is not important in this case.
     * <p>
     * LinkedVariables(target) = { field1:linkLevel1, field2:linkLevel2, ...}
     *
     * @param linkLevel       any of STATICALLY_ASSIGNED, ASSIGNED, DEPENDENT, COMMON_HC, INDEPENDENT
     * @param a               one of the two types
     * @param immutableAInput not null if you already know the immutable value of <code>a</code>
     * @param b               one of the two types, can be equal to <code>a</code>
     * @return the value for the INDEPENDENT property
     */
    public DV typesAtLinkLevel(LV linkLevel, ParameterizedType a, DV immutableAInput, ParameterizedType b) {
        if (linkLevel.isDelayed()) {
            return linkLevel.toIndependent();
        }
        if (LINK_INDEPENDENT.equals(linkLevel)) return MultiLevel.INDEPENDENT_DV;
        DV immutableA = immutableAInput == null ? typeImmutable(a) : immutableAInput;
        DV immutableB = typeImmutable(b);
        DV immutable = max(immutableA, immutableB);
        if (immutable.isDelayed()) {
            return immutable;
        }
        if (linkLevel.le(LINK_DEPENDENT)) {
            /*
             Types a and b are either equal or assignable, otherwise one cannot assign.
             We return the independent value corresponding to the most specific type, which is computed as the maximum:
             IMM->INDEPENDENT, IMM_HC -> INDEPENDENT_HC, MUTABLE -> DEPENDENT

             Why maximum? Mutable m; Object o = m;  Object is IMMUTABLE_HC.
             Because both "assignable" and "immutability" are from "higher <- lower"

             In the case of dependent, "equal" ~ "sharing mutable content"
             */
            return MultiLevel.independentCorrespondingToImmutable(immutable);
        }
        assert linkLevel.isCommonHC();
        /*
        normal rule: one of the types immutable -> INDEPENDENT, else INDEPENDENT_HC
        but: two non-recursively immutable types, COMMON_HC link, but without hidden content themselves -> independent
         */
        if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
            DV hasHCIntersection = hasHiddenContentIntersection(a, b);
            if (hasHCIntersection.isDelayed()) {
                return hasHCIntersection;
            }
            if (hasHCIntersection.valueIsFalse()) {
                return MultiLevel.INDEPENDENT_DV;
            }
        }
        return MultiLevel.independentCorrespondingToImmutable(immutable).max(MultiLevel.INDEPENDENT_HC_DV);
    }

    public DV hasHiddenContentIntersection(ParameterizedType a1, ParameterizedType a2) {
        HCAnalysis s1 = hiddenContentTypes(a1);
        HCAnalysis s2 = hiddenContentTypes(a2);
        CausesOfDelay causes = s1.delay.merge(s2.delay);
        if (causes.isDelayed()) return causes;
        return DV.fromBoolDv(s1.setOfTypes.contains(a2)
                             || s2.setOfTypes.contains(a1)
                             || !s1.setOfTypes.intersection(s2.setOfTypes).isEmpty());
    }

    private HCAnalysis hiddenContentTypes(ParameterizedType pt) {
        TypeInfo best = pt.bestTypeInfo();
        if (best == null) {
            if (pt.typeParameter == null) {
                ParameterizedType jlo = analyserContext.getPrimitives().objectParameterizedType();
                return new HCAnalysis(SetOfTypes.of(jlo), CausesOfDelay.EMPTY);
            }
            return new HCAnalysis(SetOfTypes.of(pt), CausesOfDelay.EMPTY);
        }
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(best);
        if (typeAnalysis == null) {
            return new HCAnalysis(SetOfTypes.EMPTY, CausesOfDelay.EMPTY);
        }
        CausesOfDelay delays = typeAnalysis.hiddenContentDelays();
        if (delays.isDelayed()) {
            return new HCAnalysis(null, delays);
        }
        SetOfTypes untranslated = typeAnalysis.getHiddenContentTypes();
        SetOfTypes translated = untranslated.translate(analyserContext, pt);
        return new HCAnalysis(translated, CausesOfDelay.EMPTY);
    }

    private record HCAnalysis(SetOfTypes setOfTypes, CausesOfDelay delay) {
    }

    private DV max(DV immutableA, DV immutableB) {
        // minor shortcut wrt delays
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableA)
            || MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableB)) {
            return MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
        }
        return immutableA.max(immutableB);
    }
}
