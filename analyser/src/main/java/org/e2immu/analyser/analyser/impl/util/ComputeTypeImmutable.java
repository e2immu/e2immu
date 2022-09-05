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

package org.e2immu.analyser.analyser.impl.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.Inconclusive;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.Lambda;
import org.e2immu.analyser.model.variable.FieldReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.Property.IMMUTABLE;
import static org.e2immu.analyser.analyser.Property.PARTIAL_IMMUTABLE;

/**
 * Immutable computation from the computing type analyser, separate because of its size.
 */
public record ComputeTypeImmutable(AnalyserContext analyserContext,
                                   TypeInfo typeInfo,
                                   TypeInspection typeInspection,
                                   TypeAnalysisImpl.Builder typeAnalysis,
                                   List<TypeAnalysis> parentAndOrEnclosingTypeAnalysis,
                                   List<MethodAnalyser> myMethodAnalysers,
                                   List<MethodAnalyser> myConstructors,
                                   List<FieldAnalyser> myFieldAnalysers) {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeTypeImmutable.class);

    static class Work {
        /**
         * When delayed, we compute PARTIAL_IMMUTABLE, so that some code can progress.
         */
        DV fromParentOrEnclosing;
        /**
         * takes the value IMMUTABLE, or PARTIAL_IMMUTABLE, depending on which we are computing
         */
        Property ALT_IMMUTABLE;
        /**
         * takes the status of fromParentOrEnclosing, when that is delayed
         */
        AnalysisStatus ALT_DONE;
        /**
         * either EFFECTIVE or EVENTUAL
         */
        MultiLevel.Effective parentEffective;

        /**
         * if parent is eventual, we'll have to be eventual too
         */
        boolean eventual;

        /**
         * depends on value from parent classes, and can be MUTABLE or (eventually) FINAL FIELDS
         */
        DV whenImmutableFails;

        /**
         * instrumental in the proper computation of eventual immutability
         */
        Set<FieldInfo> fieldsThatMustBeGuarded = new HashSet<>();

        /**
         * with hidden content? or without? starts at "without", can go down
         */
        int minLevel = MultiLevel.Level.IMMUTABLE.level; // can only go down!
    }

    public AnalysisStatus analyseImmutable(Analyser.SharedState sharedState) {
        // no decision has been made yet

        // Final fields: all fields must be effectively final
        DV allMyFieldsFinal = allMyFieldsFinal();
        if (allMyFieldsFinal.isDelayed()) {
            typeAnalysis.setProperty(PARTIAL_IMMUTABLE, allMyFieldsFinal);
            typeAnalysis.setProperty(Property.IMMUTABLE, allMyFieldsFinal);
            return allMyFieldsFinal.causesOfDelay();
        }

        // work = shared variables
        Work w = new Work();

        // if in a hierarchy, look at the immutability of the types we're extending
        AnalysisStatus status1 = fromParentOrEnclosing(w, sharedState);
        if (status1 != null) return status1;

        // determine the immutable level ((eventually) final fields, or mutable) in case immutable fails
        AnalysisStatus status2 = whenImmutableFails(w, sharedState, allMyFieldsFinal);
        if (status2 != null) return status2;

        // look at the preconditions
        AnalysisStatus status3 = prepWorkBeforeFields(w, sharedState);
        if (status3 != null) return status3;

        // find out which fields must be guarded by a mark
        AnalysisStatus status4 = loopOverFields(w, sharedState);
        if (status4 != null) return status4;

        // constructors must be @Independent, with/without hidden content
        AnalysisStatus status5 = loopOverConstructors(w, sharedState);
        if (status5 != null) return status5;

        // methods must be @NotModified, and @Independent, with/without hidden content
        // modifying methods must be guarded in the eventual case
        AnalysisStatus status6 = loopOverMethods(w, sharedState);
        if (status6 != null) return status6;

        AnalysisStatus status7 = checkFieldsThatMustBeGuarded(w);
        if (status7 != null) return status7;

        MultiLevel.Effective effective = w.eventual ? MultiLevel.Effective.EVENTUAL : MultiLevel.Effective.EFFECTIVE;
        DV immutableWithoutSuperTypes = MultiLevel.composeImmutable(effective, w.minLevel);
        DV superTypesIncluded = includeSuperTypes(w.fromParentOrEnclosing, immutableWithoutSuperTypes);
        DV finalValue = accountForExtensibility(superTypesIncluded);
        LOGGER.debug("Set {} of type {} to {}", w.ALT_IMMUTABLE, typeInfo.fullyQualifiedName, finalValue);
        return doneImmutable(w.ALT_IMMUTABLE, finalValue, w.ALT_DONE);
    }

    private DV accountForExtensibility(DV immutable) {
        if (typeInspection.typeNature() == TypeNature.CLASS) {
            int level = MultiLevel.level(immutable);
            if (typeInspection.isExtensible()) {
                // abstract classes must have hidden content
                if (level == MultiLevel.Level.IMMUTABLE.level && typeInspection.isAbstract()) {
                    return MultiLevel.composeImmutable(MultiLevel.effective(immutable), MultiLevel.Level.IMMUTABLE_HC.level);
                }
            } else if (level == MultiLevel.Level.IMMUTABLE_HC.level) {
                // not extensible, IMMUTABLE_HC: check hidden content, if empty, return IMMUTABLE
                Set<ParameterizedType> superTypes = typeInfo.superTypes(analyserContext);
                Set<ParameterizedType> hiddenContent = new HashSet<>(typeAnalysis.getHiddenContentTypes().types());
                hiddenContent.removeAll(superTypes);
                if (hiddenContent.isEmpty()) {
                    return MultiLevel.composeImmutable(MultiLevel.effective(immutable), MultiLevel.Level.IMMUTABLE.level);
                }
            }
        }
        return immutable;
    }

    private DV includeSuperTypes(DV fromParentOrEnclosing, DV immutableWithoutSuperTypes) {
        assert fromParentOrEnclosing.gt(MultiLevel.MUTABLE_DV);
        int levelParent = MultiLevel.level(fromParentOrEnclosing);
        if (MultiLevel.Level.IMMUTABLE_HC.level == levelParent && parentOrEnclosingHaveNoHiddenContent()) {
            /*
             this can be due to the fact that the parent is an interface, which must have HC if it is immutable
             we study the hidden content of the parent; if empty, we return only our immutable value.
             Example: deriving from java.lang.Enum (as each enumeration does) means that the immutable value of
             Enum is not relevant, as it has no hidden content.
             */
            return immutableWithoutSuperTypes;
        }
        // e.g. final fields + immutable hc -> ff
        return fromParentOrEnclosing.min(immutableWithoutSuperTypes);
    }

    private AnalysisStatus checkFieldsThatMustBeGuarded(Work w) {
        if (!w.fieldsThatMustBeGuarded.isEmpty()) {
            // check that these fields occur only in tandem to eventually immutable fields; if not, return failure
            Set<FieldInfo> eventuallyImmutable = typeAnalysis.getEventuallyImmutableFields();
            if (!eventuallyImmutable.isEmpty()) {
                Map<FieldInfo, Set<MethodInfo>> methodsOfEventuallyImmutableFields =
                        eventuallyImmutable.stream().collect(Collectors.toUnmodifiableMap(f -> f, this::methodsOf));
                w.fieldsThatMustBeGuarded.removeIf(f -> {
                    Set<MethodInfo> methodsOfField = methodsOf(f);
                    boolean remove = methodsOfEventuallyImmutableFields.values().stream().anyMatch(
                            ev -> ev.containsAll(methodsOfField));
                    if (remove) {
                        typeAnalysis.addGuardedByEventuallyImmutableField(f);
                    }
                    return remove;
                });
            }
            if (!w.fieldsThatMustBeGuarded.isEmpty()) {
                LOGGER.debug("Set @Immutable of type {} to {}, fieldsThatMustBeGuarded not empty", typeInfo,
                        w.whenImmutableFails);
                return doneImmutable(w.ALT_IMMUTABLE, w.whenImmutableFails, w.ALT_DONE);
            }
        }
        return null;
    }

    private AnalysisStatus loopOverMethods(Work w, Analyser.SharedState sharedState) {
        CausesOfDelay causesMethods = CausesOfDelay.EMPTY;
        for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
            DV modified = methodAnalyser.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD_ALT_TEMP);
            // in the eventual case, we only need to look at the non-modifying methods
            // calling a modifying method will result in an error
            if (modified.valueIsFalse()) {
                causesMethods = causesMethods.merge(nonModifyingMethod(w, methodAnalyser));
            } else if (modified.valueIsTrue()) {
                causesMethods = causesMethods.merge(modifyingMethod(w, methodAnalyser));
            } // excluded earlier in approved preconditions for immutability: no idea about modification, ignored
        }
        if (causesMethods.isDelayed()) {
            return delayImmutable(causesMethods, sharedState.allowBreakDelay(), w.whenImmutableFails);
        }
        return null;
    }

    private CausesOfDelay modifyingMethod(Work w, MethodAnalyser methodAnalyser) {
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        if (typeAnalysis.isEventual()) {
            // code identical to that of constructors
            for (ParameterAnalysis parameterAnalysis : methodAnalyser.getParameterAnalyses()) {
                DV independent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                if (independent.isDelayed()) {
                    if (parameterAnalysis.getParameterInfo().parameterizedType.typeInfo != typeInfo) {
                        LOGGER.debug("Cannot decide yet about {} of {}, no info on @Independent for parameter {}",
                                w.ALT_IMMUTABLE, typeInfo, parameterAnalysis);
                        typeAnalysis.setProperty(w.ALT_IMMUTABLE, independent);
                        causes = causes.merge(independent.causesOfDelay()); //not decided
                    }
                } else {
                    DV correctedIndependent = correctIndependentFunctionalInterface(parameterAnalysis, independent);
                    int correspondingImmutableLevel = MultiLevel.correspondingImmutableLevel(correctedIndependent);
                    w.minLevel = Math.min(w.minLevel, correspondingImmutableLevel);
                }
            }
        } else {
            throw new UnsupportedOperationException("Already in negative");
        }
        return causes;
    }

    private CausesOfDelay nonModifyingMethod(Work w, MethodAnalyser methodAnalyser) {
        if (methodAnalyser.getMethodInfo().isVoid()) {
            // we're looking at return types
            return CausesOfDelay.EMPTY;
        }
        DV returnTypeImmutable = methodAnalyser.getMethodAnalysis().getProperty(Property.IMMUTABLE);

        ParameterizedType returnType;
        Expression srv = methodAnalyser.getMethodAnalysis().getSingleReturnValue();
        if (srv.isDone()) {
            // concrete
            returnType = srv.returnType();
        } else {
            // formal; this one may come earlier, but that's OK; the only thing it can do is facilitate a delay
            returnType = analyserContext.getMethodInspection(methodAnalyser.getMethodInfo()).getReturnType();
        }
        boolean returnTypePartOfMyself = isOfOwnOrInnerClassType(returnType, typeInfo);
        if (returnTypePartOfMyself) return CausesOfDelay.EMPTY;
        if (returnTypeImmutable.isDelayed()) {
            if (srv.causesOfDelay().containsCauseOfDelay(CauseOfDelay.Cause.BREAK_IMMUTABLE_DELAY)) {
                LOGGER.debug("Breaking @Immutable delay self reference on {}", methodAnalyser);
                return CausesOfDelay.EMPTY;
            }
            LOGGER.debug("Return type of {} has delayed IMMUTABLE property, delaying", methodAnalyser);
            CausesOfDelay marker = methodAnalyser.getMethodInfo().delay(CauseOfDelay.Cause.BREAK_IMMUTABLE_DELAY);
            return returnTypeImmutable.causesOfDelay().merge(marker);
        }
        // TODO while it works at the moment, the code is a bit of a mess (indep checks only for identical types, check on srv and returnTypeImmutable, ...)
        MultiLevel.Effective returnTypeEffectiveImmutable = MultiLevel.effectiveAtImmutableLevel(returnTypeImmutable);
        if (returnTypeEffectiveImmutable.lt(MultiLevel.Effective.EVENTUAL)) {
            // rule 5, continued: if not primitive, not Immutable, then the result must be Independent of the support types
            DV independent = methodAnalyser.getMethodAnalysis().getProperty(Property.INDEPENDENT);
            if (independent.isDelayed()) {
                if (returnType.typeInfo == typeInfo) {
                    LOGGER.debug("Cannot decide if method {} is independent, but given that its return type is a self reference, don't care",
                            methodAnalyser);
                } else {
                    LOGGER.debug("Cannot decide yet if {} is an immutable type; not enough info on whether the method {} is @Independent",
                            typeInfo, methodAnalyser);
                    return independent.causesOfDelay();
                }
            }
            if (independent.equals(MultiLevel.DEPENDENT_DV)) {
                throw new UnsupportedOperationException("Already in negative");
            }
            int correspondingImmutableLevel = MultiLevel.correspondingImmutableLevel(independent);
            w.minLevel = Math.min(w.minLevel, correspondingImmutableLevel);
        } else {
            w.minLevel = Math.min(w.minLevel, MultiLevel.level(returnTypeImmutable));
        }
        return CausesOfDelay.EMPTY;
    }

    private AnalysisStatus loopOverConstructors(Work w, Analyser.SharedState sharedState) {
        CausesOfDelay causesConstructor = CausesOfDelay.EMPTY;
        for (MethodAnalyser constructor : myConstructors) {
            for (ParameterAnalysis parameterAnalysis : constructor.getParameterAnalyses()) {
                DV independent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                if (independent.isDelayed()) {
                    if (parameterAnalysis.getParameterInfo().parameterizedType.typeInfo == typeInfo) {
                        continue;
                    }
                    LOGGER.debug("Cannot decide yet about immutable type, no info on @Independent in constructor {}",
                            constructor);
                    causesConstructor = causesConstructor.merge(independent.causesOfDelay()); //not decided
                } else {
                    DV correctedIndependent = correctIndependentFunctionalInterface(parameterAnalysis, independent);
                    if (correctedIndependent.equals(MultiLevel.DEPENDENT_DV)) {
                        throw new UnsupportedOperationException("Already in negative");
                    }
                    int correspondingImmutableLevel = MultiLevel.correspondingImmutableLevel(correctedIndependent);
                    w.minLevel = Math.min(w.minLevel, correspondingImmutableLevel);
                }
            }
        }
        if (causesConstructor.isDelayed()) {
            return delayImmutable(causesConstructor, sharedState.allowBreakDelay(), w.whenImmutableFails);
        }
        return null; // continue beyond constructors
    }

    private AnalysisStatus loopOverFields(Work w, Analyser.SharedState sharedState) {
        CausesOfDelay causesFields = CausesOfDelay.EMPTY;

        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            FieldAnalysis fieldAnalysis = fieldAnalyser.getFieldAnalysis();
            FieldInfo fieldInfo = fieldAnalyser.getFieldInfo();
            if (fieldInfo.type.bestTypeInfo() == typeInfo) {
                // "self" type, ignored
                continue;
            }

            FieldReference thisFieldInfo = new FieldReference(analyserContext, fieldInfo);

            // RULE 1: ALL FIELDS MUST BE NOT MODIFIED

            // this follows automatically if they are primitive or immutable themselves
            // because of down-casts on non-primitives, e.g. from java.lang.Object to explicit, we cannot rely on the static type
            DV fieldImmutable = fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
            MultiLevel.Effective fieldEffectiveImmutable = MultiLevel.effectiveAtImmutableLevel(fieldImmutable);
            if (fieldImmutable.isDelayed()) {
                if (fieldIsOfOwnOrInnerClassType(fieldInfo)) {
                    TypeInfo ownOrInner = fieldInfo.type.typeInfo;
                    assert ownOrInner != null;
                    // non-static nested types (inner types such as lambda's, anonymous)
                    if (ownOrInner == typeInfo) {
                        fieldEffectiveImmutable = MultiLevel.Effective.EFFECTIVE; // doesn't matter at all
                    } else {
                        // inner type, try partial
                        DV partial = analyserContext.getTypeAnalysis(ownOrInner).getProperty(Property.PARTIAL_IMMUTABLE);
                        if (partial.isDelayed()) {
                            LOGGER.debug("Field {} of nested type has no PARTIAL_IMMUTABLE yet", fieldInfo);
                            causesFields = causesFields.merge(fieldImmutable.causesOfDelay());
                            continue;
                        }
                        fieldEffectiveImmutable = MultiLevel.effectiveAtImmutableLevel(partial);
                    }
                } else {
                    // field is of a type that is very closely related to the type being analysed; we're looking to break a delay
                    // here by requiring the rules, and saying that it is not eventual; see FunctionInterface_0
                    ParameterizedType concreteType = fieldAnalysis.concreteTypeNullWhenDelayed();
                    if (concreteType != null && concreteType.typeInfo != null &&
                            concreteType.typeInfo.topOfInterdependentClassHierarchy() == typeInfo.topOfInterdependentClassHierarchy()) {
                        fieldEffectiveImmutable = MultiLevel.Effective.EVENTUAL_AFTER; // must follow rules, but is not eventual
                    } else {
                        LOGGER.debug("Field {} not known yet if of immutable type, delaying immutable on type", fieldInfo);
                        causesFields = causesFields.merge(fieldImmutable.causesOfDelay());
                    }
                }
            }

            // NOTE: the 2 values that matter now are EVENTUAL and EFFECTIVE; any other will lead to a field
            // that needs to follow the additional rules
            boolean isPrimitive = fieldInfo.type.isPrimitiveExcludingVoid();

            if (fieldEffectiveImmutable == MultiLevel.Effective.EVENTUAL || fieldEffectiveImmutable == MultiLevel.Effective.EVENTUAL_BEFORE) {
                w.eventual = true;
                assert !typeAnalysis.eventuallyImmutableFieldNotYetSet(fieldInfo) : "Already in negative";
                assert typeAnalysis.isEventual();
            } else if (typeAnalysis.getGuardedByEventuallyImmutableFields().contains(fieldInfo)) {
                LOGGER.debug("Field {} is guarded by preconditions", fieldInfo);

            } else if (!isPrimitive) {
                DV modified = fieldAnalysis.getProperty(Property.MODIFIED_OUTSIDE_METHOD);

                // we check on !eventual, because in the eventual case, there are no modifying methods callable anymore
                if (!w.eventual && modified.isDelayed()) {
                    LOGGER.debug("Field {} not known yet if @NotModified, delaying immutable on type", fieldInfo);
                    causesFields = causesFields.merge(modified.causesOfDelay());
                    continue;
                }
                if (modified.valueIsTrue()) {
                    if (typeAnalysis.containsApprovedPreconditionsImmutable(thisFieldInfo)) {
                        LOGGER.debug("Modified field {} has the approved preconditions for {} to be eventually immutable",
                                fieldInfo, typeInfo);
                    } else {
                        LOGGER.debug("For {} to become eventually immutable, modified field {} can only be modified " +
                                "in methods marked @Mark or @Only(before=)", typeInfo, fieldInfo);
                        w.fieldsThatMustBeGuarded.add(fieldInfo);
                    }
                }

                // RULE 2: ALL NON-IMMUTABLE TYPES MUST HAVE ACCESS MODIFIER PRIVATE
                if (fieldInfo.type.typeInfo != typeInfo) {
                    boolean fieldRequiresRules = fieldImmutable.isDone() && fieldEffectiveImmutable != MultiLevel.Effective.EFFECTIVE;
                    if (!fieldInfo.fieldInspection.get().isPrivate() && fieldRequiresRules) {
                        throw new UnsupportedOperationException("Already in negative");
                    }
                } else {
                    LOGGER.debug("Ignoring private modifier check of {}, self-referencing", fieldInfo);
                }
            }
            /*
             if the field is not recursively immutable, we have to allow for hidden content (w.minLevel cannot
             have its maximal value of IMMUTABLE.level anymore).
             */
            if (fieldImmutable.isDelayed()) {
                // this delay is not caught because of the partial data... still, we cannot conclude, see e.g. E2Immutable_10
                LOGGER.debug("Delaying immutable of {} because field {} has immutable delayed", typeInfo, fieldInfo);
                return delayImmutable(fieldImmutable.causesOfDelay(), sharedState.allowBreakDelay(), w.whenImmutableFails);
            }
            int fieldImmutableLevel = MultiLevel.level(fieldImmutable);
            if (fieldImmutableLevel < MultiLevel.Level.IMMUTABLE.level) {
                w.minLevel = Math.min(w.minLevel, MultiLevel.Level.IMMUTABLE_HC.level);
            }
        }
        if (causesFields.isDelayed()) {
            LOGGER.debug("Delaying immutable of {} because of fields, delays: {}", typeInfo, causesFields);
            return delayImmutable(causesFields, sharedState.allowBreakDelay(), w.whenImmutableFails);
        }

        return null; // continue beyond fields
    }

    private AnalysisStatus prepWorkBeforeFields(Work w, Analyser.SharedState sharedState) {

        // NOTE that we need to check 2x: needed in else of previous statement, but also if we get through if-side.
        CausesOfDelay approvedDelays = typeAnalysis.approvedPreconditionsStatus(true);
        if (approvedDelays.isDelayed()) {
            LOGGER.debug("Type {} is not effectively level 1 immutable, waiting for" +
                    " preconditions to find out if it is eventually level 2 immutable", typeInfo);
            return delayImmutable(approvedDelays, sharedState.allowBreakDelay(), w.whenImmutableFails);
        }

        /*
         this group of code offers a shortcut, see e.g. Independent_4, MethodInfo.
         It takes a long time to declare the field "names" @NotModified, but there as a method
         exposing it... so no point in waiting!!
         */
        AnalysisStatus negativeOrEventualFields = negativeAndEventuallyImmutableFields(w);
        if (negativeOrEventualFields != null) return negativeOrEventualFields;
        AnalysisStatus negativeConstructors = negativeConstructors(w);
        if (negativeConstructors != null) return negativeConstructors;
        return negativeMethods(w); // continue when null!!
    }

    private AnalysisStatus whenImmutableFails(Work w, Analyser.SharedState sharedState, DV allMyFieldsFinal) {

        DV whenImmutableFails;
        if (allMyFieldsFinal.valueIsFalse() || w.parentEffective != MultiLevel.Effective.EFFECTIVE) {
            CausesOfDelay approvedDelays = typeAnalysis.approvedPreconditionsStatus(false);
            if (approvedDelays.isDelayed()) {
                LOGGER.debug("Type {} is not effectively level 1 immutable, waiting for" +
                        " preconditions to find out if it is eventually level 1 immutable", typeInfo);
                return delayImmutable(approvedDelays, sharedState.allowBreakDelay(), MultiLevel.MUTABLE_DV);
            }
            List<FieldReference> nonFinalFields = myFieldAnalysers.stream()
                    .filter(fa -> DV.FALSE_DV.equals(fa.getFieldAnalysis().getProperty(Property.FINAL)))
                    .map(fa -> new FieldReference(analyserContext, fa.getFieldInfo())).toList();
            Set<FieldInfo> fieldsNotE1 = typeAnalysis.nonFinalFieldsNotApprovedOrGuarded(nonFinalFields);
            if (!fieldsNotE1.isEmpty()) {
                whenImmutableFails = MultiLevel.MUTABLE_DV;
                w.fieldsThatMustBeGuarded.addAll(fieldsNotE1);
            } else {
                whenImmutableFails = MultiLevel.EVENTUALLY_FINAL_FIELDS_DV;
            }
            w.eventual = true;

            if (w.parentEffective == MultiLevel.Effective.EVENTUAL) {
                TypeAnalysis parentTypeAnalysis = analyserContext.getTypeAnalysis(typeInspection.parentClass().typeInfo);
                Set<FieldInfo> parentFields = parentTypeAnalysis.getEventuallyImmutableFields();
                assert !parentFields.isEmpty() ||
                        !parentTypeAnalysis.getApprovedPreconditionsFinalFields().isEmpty() ||
                        !parentTypeAnalysis.getApprovedPreconditionsImmutable().isEmpty();
                parentFields.forEach(fieldInfo -> {
                    if (typeAnalysis.eventuallyImmutableFieldNotYetSet(fieldInfo)) {
                        typeAnalysis.addEventuallyImmutableField(fieldInfo);
                    }
                });
            }
        } else {
            CausesOfDelay approvedDelays = typeAnalysis.approvedPreconditionsStatus(true);
            if (approvedDelays.isDelayed()) {
                LOGGER.debug("Type {} is effectively level 1 immutable, waiting for" +
                        " preconditions to find out if it is eventually level 2 immutable", typeInfo);
                return delayImmutable(approvedDelays, sharedState.allowBreakDelay(), MultiLevel.MUTABLE_DV);
            }
            whenImmutableFails = MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV;
            // it is possible that all fields are final, yet some field's content is used as the precondition
            w.eventual = !typeAnalysis.approvedPreconditionsImmutableIsEmpty();
        }

        w.whenImmutableFails = w.fromParentOrEnclosing.min(whenImmutableFails);
        return null; // continue
    }

    private boolean parentOrEnclosingHaveNoHiddenContent() {
        return parentAndOrEnclosingTypeAnalysis.stream().allMatch(ta -> ta.getHiddenContentTypes().isEmpty());
    }

    private AnalysisStatus fromParentOrEnclosing(Work w, Analyser.SharedState sharedState) {
        DV partialImmutable = typeAnalysis.getProperty(Property.PARTIAL_IMMUTABLE);
        w.fromParentOrEnclosing = parentAndOrEnclosingTypeAnalysis.stream()
                .map(typeAnalysis -> typeAnalysis.getProperty(Property.IMMUTABLE))
                .reduce(Property.IMMUTABLE.bestDv, DV::min);

        // parent: we need to know if it is EFFECTIVE, or EVENTUAL
        w.parentEffective = effectiveImmutableOfParent();
        if (w.fromParentOrEnclosing.isDelayed()) {
            typeAnalysis.setProperty(Property.IMMUTABLE, w.fromParentOrEnclosing);
            if (partialImmutable.isDone()) {
                if (sharedState.allowBreakDelay()) {
                    return delayImmutable(w.fromParentOrEnclosing.causesOfDelay(), true, MultiLevel.MUTABLE_DV);
                }
                LOGGER.debug("We've done what we can, waiting for parent-enclosing now");
                return AnalysisStatus.of(w.fromParentOrEnclosing);
            }
            LOGGER.debug("Continuing in {}, ignore parent-enclosing", typeInfo);
            w.ALT_IMMUTABLE = Property.PARTIAL_IMMUTABLE;
            w.ALT_DONE = AnalysisStatus.of(w.fromParentOrEnclosing);
            w.fromParentOrEnclosing = Property.IMMUTABLE.bestDv;
            w.parentEffective = MultiLevel.Effective.EFFECTIVE;
        } else {
            if (w.fromParentOrEnclosing.equals(MultiLevel.MUTABLE_DV)) {
                LOGGER.debug("{} is mutable, because parent or enclosing is mutable", typeInfo);
                return doneImmutable(IMMUTABLE, MultiLevel.MUTABLE_DV, DONE);
            }
            if (partialImmutable.isDone()) {
                DV min = w.fromParentOrEnclosing.min(partialImmutable);
                DV finalValue = accountForExtensibility(min);
                typeAnalysis.setProperty(Property.IMMUTABLE, finalValue);
                LOGGER.debug("We had already done the work without parent/enclosing, now its there: {}", finalValue);
                return DONE;
            }
            w.ALT_IMMUTABLE = Property.IMMUTABLE;
            w.ALT_DONE = AnalysisStatus.DONE;
        }

        return null; // continue work

    }

    private MultiLevel.Effective effectiveImmutableOfParent() {
        ParameterizedType parentClass = typeInspection.parentClass();
        if (parentClass != null && parentClass.isJavaLangObject()) {
            return MultiLevel.Effective.EFFECTIVE;
        }
        assert parentClass != null;
        TypeInfo parentType = parentClass.typeInfo;
        DV parentImmutable = analyserContext.getTypeAnalysis(parentType).getProperty(Property.IMMUTABLE);
        return MultiLevel.effectiveAtFinalFields(parentImmutable);
    }

    private AnalysisStatus negativeAndEventuallyImmutableFields(Work w) {
        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            FieldAnalysis fieldAnalysis = fieldAnalyser.getFieldAnalysis();
            FieldInfo fieldInfo = fieldAnalyser.getFieldInfo();
            if (fieldInfo.type.bestTypeInfo() == typeInfo) {
                continue;
            }
            DV fieldImmutable = fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
            MultiLevel.Effective fieldEffectiveImmutable = MultiLevel.effectiveAtImmutableLevel(fieldImmutable);
            if (fieldImmutable.isDelayed()) {
                if (fieldIsOfOwnOrInnerClassType(fieldInfo)) {
                    TypeInfo ownOrInner = fieldInfo.type.typeInfo;
                    assert ownOrInner != null;
                    // non-static nested types (inner types such as lambda's, anonymous)
                    if (ownOrInner == typeInfo) {
                        fieldEffectiveImmutable = MultiLevel.Effective.EFFECTIVE; // doesn't matter at all
                    } else {
                        // inner type, try partial
                        DV partial = analyserContext.getTypeAnalysis(ownOrInner).getProperty(Property.PARTIAL_IMMUTABLE);
                        if (partial.isDelayed()) {
                            continue;
                        }
                        fieldEffectiveImmutable = MultiLevel.effectiveAtImmutableLevel(partial);
                    }
                } else {
                    // field is of a type that is very closely related to the type being analysed; we're looking to break a delay
                    // here by requiring the rules, and saying that it is not eventual; see FunctionInterface_0
                    ParameterizedType concreteType = fieldAnalysis.concreteTypeNullWhenDelayed();
                    if (concreteType != null && concreteType.typeInfo != null &&
                            concreteType.typeInfo.topOfInterdependentClassHierarchy() == typeInfo.topOfInterdependentClassHierarchy()) {
                        fieldEffectiveImmutable = MultiLevel.Effective.EVENTUAL_AFTER; // must follow rules, but is not eventual
                    }
                }
            }
            boolean isPrimitive = fieldInfo.type.isPrimitiveExcludingVoid();

            if (fieldEffectiveImmutable == MultiLevel.Effective.EVENTUAL || fieldEffectiveImmutable == MultiLevel.Effective.EVENTUAL_BEFORE) {
                if (typeAnalysis.eventuallyImmutableFieldNotYetSet(fieldInfo)) {
                    typeAnalysis.addEventuallyImmutableField(fieldInfo);
                }
            } else if (!isPrimitive && !typeAnalysis.getGuardedByEventuallyImmutableFields().contains(fieldInfo)) {
                boolean fieldRequiresRules = fieldImmutable.isDone() && fieldEffectiveImmutable != MultiLevel.Effective.EFFECTIVE;
                if (fieldInfo.type.typeInfo != typeInfo) {
                    if (!fieldInfo.fieldInspection.get().isPrivate() && fieldRequiresRules) {
                        LOGGER.debug("{} is not an immutable class, because field {} is not primitive, " +
                                "not immutable itself, and also exposed (not private)", typeInfo, fieldInfo);
                        return doneImmutable(w.ALT_IMMUTABLE, w.whenImmutableFails, w.ALT_DONE);
                    }
                }
            }
        }
        return null;
    }

    private AnalysisStatus negativeConstructors(Work w) {
        for (MethodAnalyser constructor : myConstructors) {
            for (ParameterAnalysis parameterAnalysis : constructor.getParameterAnalyses()) {
                DV independent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                if (independent.isDone()) {
                    DV correctedIndependent = correctIndependentFunctionalInterface(parameterAnalysis, independent);
                    if (correctedIndependent.equals(MultiLevel.DEPENDENT_DV)) {
                        LOGGER.debug("{} is not an immutable class, because a constructor is @Dependent", typeInfo);
                        return doneImmutable(w.ALT_IMMUTABLE, w.whenImmutableFails, w.ALT_DONE);
                    }
                }
            }
        }
        return null;
    }

    private AnalysisStatus negativeMethods(Work w) {
        for (MethodAnalyser methodAnalyser : myMethodAnalysers) {
            DV modified = methodAnalyser.getMethodAnalysis().getProperty(Property.MODIFIED_METHOD_ALT_TEMP);
            // in the eventual case, we only need to look at the non-modifying methods
            // calling a modifying method will result in an error
            if (modified.valueIsFalse()) {
                if (methodAnalyser.getMethodInfo().isVoid()) continue; // we're looking at return types
                DV returnTypeImmutable = methodAnalyser.getMethodAnalysis().getProperty(Property.IMMUTABLE);

                ParameterizedType returnType;
                Expression srv = methodAnalyser.getMethodAnalysis().getSingleReturnValue();
                if (srv.isDone()) {
                    // concrete
                    returnType = srv.returnType();
                } else {
                    // formal; this one may come earlier, but that's OK; the only thing it can do is facilitate a delay
                    returnType = analyserContext.getMethodInspection(methodAnalyser.getMethodInfo()).getReturnType();
                }
                boolean returnTypePartOfMyself = isOfOwnOrInnerClassType(returnType, typeInfo);
                if (returnTypePartOfMyself) {
                    continue;
                }
                if (returnTypeImmutable.isDelayed()) {
                    continue;
                }
                MultiLevel.Effective returnTypeEffectiveImmutable = MultiLevel.effectiveAtImmutableLevel(returnTypeImmutable);
                if (returnTypeEffectiveImmutable.lt(MultiLevel.Effective.EVENTUAL)) {
                    DV independent = methodAnalyser.getMethodAnalysis().getProperty(Property.INDEPENDENT);
                    if (independent.equals(MultiLevel.DEPENDENT_DV)) {
                        LOGGER.debug("{} is not an immutable class, because method {}'s return type is not primitive, not immutable, not independent",
                                typeInfo, methodAnalyser);
                        return doneImmutable(w.ALT_IMMUTABLE, w.whenImmutableFails, w.ALT_DONE);
                    }
                }
            } else if (modified.valueIsTrue()) {
                if (typeAnalysis.isEventual()) {
                    // code identical to that of constructors
                    for (ParameterAnalysis parameterAnalysis : methodAnalyser.getParameterAnalyses()) {
                        DV independent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                        if (independent.isDone()) {
                            DV correctedIndependent = correctIndependentFunctionalInterface(parameterAnalysis, independent);
                            if (correctedIndependent.equals(MultiLevel.DEPENDENT_DV)) {
                                LOGGER.debug("{} is not an immutable class, because constructor is @Dependent", typeInfo);
                                return doneImmutable(w.ALT_IMMUTABLE, w.whenImmutableFails, w.ALT_DONE);
                            }
                        }
                    }
                } else {
                    // contracted @Modified, see e.g. InlinedMethod_AAPI_3
                    LOGGER.debug("{} is not an immutable class, because method {} is modifying (even though none of our fields are)",
                            typeInfo, methodAnalyser);
                    return doneImmutable(w.ALT_IMMUTABLE, w.whenImmutableFails, w.ALT_DONE);
                }
            }
        }
        return null;
    }

    /*
    property == IMMUTABLE -> also write PARTIAL_IMMUTABLE
    property == PARTIAL_IMMUTABLE -> only write partial
     */
    private AnalysisStatus doneImmutable(Property property, DV value, AnalysisStatus analysisStatus) {
        typeAnalysis.setProperty(property, value);
        if (property == IMMUTABLE) {
            typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_IMMUTABLE, value);
        }
        return analysisStatus;
    }

    private AnalysisStatus delayImmutable(CausesOfDelay delays, boolean allowBreakDelay, DV baseValue) {
        DV value;
        if (allowBreakDelay) {
            LOGGER.debug("Breaking delay in IMMUTABLE, type {}", typeInfo);
            value = new Inconclusive(baseValue);
        } else {
            value = delays;
        }
        typeAnalysis.setProperty(IMMUTABLE, value);
        typeAnalysis.setPropertyIfAbsentOrDelayed(PARTIAL_IMMUTABLE, value);
        return allowBreakDelay ? DONE : delays;
    }


    private Set<MethodInfo> methodsOf(FieldInfo fieldInfo) {
        return myMethodAnalysers.stream()
                .filter(ma -> ma.getFieldAsVariableStream(fieldInfo).anyMatch(ComputeTypeImmutable::isModified) ||
                        ma.getMethodAnalysis().getPreconditionForEventual().guardsField(analyserContext, fieldInfo))
                .map(MethodAnalyser::getMethodInfo)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isModified(VariableInfo vi) {
        return vi.isAssigned() || vi.getProperty(Property.CONTEXT_MODIFIED).valueIsTrue();
    }

    private DV allMyFieldsFinal() {
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        for (FieldAnalyser fieldAnalyser : myFieldAnalysers) {
            DV effectivelyFinal = fieldAnalyser.getFieldAnalysis().getProperty(Property.FINAL);
            if (effectivelyFinal.isDelayed()) {
                LOGGER.debug("Delay on type {}, field {} effectively final not known yet", typeInfo, fieldAnalyser);
                causes = causes.merge(effectivelyFinal.causesOfDelay());
            }
            if (effectivelyFinal.valueIsFalse()) {
                LOGGER.debug("Type {} cannot be @E1Immutable, field {} is not effectively final", typeInfo, fieldAnalyser);
                return DV.FALSE_DV;
            }
        }
        if (causes.isDelayed()) return causes;
        return DV.TRUE_DV;
    }


    private boolean fieldIsOfOwnOrInnerClassType(FieldInfo fieldInfo) {
        if (isOfOwnOrInnerClassType(fieldInfo.type, typeInfo)) {
            return true;
        }
        // the field can be assigned to an anonymous type, which has a static functional interface type
        // we want to catch the newly created type
        TypeInfo anonymousType = initializerAssignedToAnonymousType(fieldInfo);
        return anonymousType != null && anonymousType.isEnclosedIn(typeInfo);
    }


    public static boolean isOfOwnOrInnerClassType(ParameterizedType type, TypeInfo typeInfo) {
        return type.typeInfo != null && type.typeInfo.isEnclosedIn(typeInfo);
    }


    private TypeInfo initializerAssignedToAnonymousType(FieldInfo fieldInfo) {
        FieldInspection.FieldInitialiser initialiser = fieldInfo.fieldInspection.get().getFieldInitialiser();
        if (initialiser == null) return null;
        Expression expression = initialiser.initialiser();
        if (expression == null || expression == EmptyExpression.EMPTY_EXPRESSION) return null;
        ParameterizedType type = expression.returnType();
        if (type.isFunctionalInterface()) {
            if (expression instanceof ConstructorCall cc && cc.anonymousClass() != null) {
                return cc.anonymousClass();
            }
            if (expression instanceof Lambda lambda) {
                return lambda.definesType();
            }
        }
        return type.typeInfo;
    }

    /*
    See Lazy; other code in SAEvaluationContext.cannotBeModified and StatementAnalysisImpl.initializeParameter.
    A functional interface comes in as the parameter of a non-private method. Modifications on its single, modifying
    method are ignored. As a consequence, we treat the object as at least immutable - independent.
     */
    public static DV correctIndependentFunctionalInterface(ParameterAnalysis parameterAnalysis, DV independent) {
        DV correctedIndependent;
        DV ignoreModification = parameterAnalysis.getProperty(Property.IGNORE_MODIFICATIONS);
        if (ignoreModification.equals(MultiLevel.IGNORE_MODS_DV)
                && parameterAnalysis.getParameterInfo().parameterizedType.isFunctionalInterface()
                && !parameterAnalysis.getParameterInfo().getMethod().methodInspection.get().isPrivate()) {
            LOGGER.debug("Incoming functional interface on non-private method");
            correctedIndependent = independent.max(MultiLevel.INDEPENDENT_HC_DV);
        } else {
            correctedIndependent = independent;
        }
        return correctedIndependent;
    }

}
