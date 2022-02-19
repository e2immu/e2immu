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
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MultiValue;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExplicitConstructorInvocation;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.resolver.impl.ResolverImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE_ALL;
import static org.e2immu.analyser.analyser.LinkedVariables.ASSIGNED_DV;
import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.config.AnalyserProgram.Step.ITERATION_0;
import static org.e2immu.analyser.config.AnalyserProgram.Step.ITERATION_1PLUS;
import static org.e2immu.analyser.model.MultiLevel.*;
import static org.e2immu.analyser.model.MultiLevel.Effective.*;

public class ComputedParameterAnalyser extends ParameterAnalyserImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputedParameterAnalyser.class);
    public static final String CHECK_UNUSED_PARAMETER = "PA:checkUnusedParameter";
    public static final String ANALYSE_FIRST_ITERATION = "PA:analyseFirstIteration";
    public static final String ANALYSE_FIELD_ASSIGNMENTS = "PA:analyseFieldAssignments";
    public static final String ANALYSE_CONTEXT = "PA:analyseContext";
    public static final String ANALYSE_INDEPENDENT_NO_ASSIGNMENT = "PA:analyseIndependentNoAssignment";
    public static final String ANALYSE_CONTAINER_NO_ASSIGNMENT = "PA:analyseContainerNoAssignment";

    private Map<FieldInfo, FieldAnalyser> fieldAnalysers;

    public ComputedParameterAnalyser(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
        super(analyserContext, parameterInfo);
    }

    @Override
    public void initialize(Stream<FieldAnalyser> fieldAnalyserStream) {
        this.fieldAnalysers = fieldAnalyserStream.collect(Collectors.toUnmodifiableMap(FieldAnalyser::getFieldInfo, fa -> fa));
    }

    record SharedState(int iteration) {
    }

    public final AnalyserComponents<String, SharedState> analyserComponents =
            new AnalyserComponents.Builder<String, SharedState>(analyserContext.getAnalyserProgram())
                    .add(CHECK_UNUSED_PARAMETER, ITERATION_0, this::checkUnusedParameter)
                    .add(ANALYSE_FIRST_ITERATION, ITERATION_0, this::analyseFirstIteration)
                    .add(ANALYSE_FIELD_ASSIGNMENTS, ITERATION_1PLUS, this::analyseFieldAssignments)
                    .add(ANALYSE_CONTEXT, ITERATION_1PLUS, this::analyseContext)
                    .add(ANALYSE_INDEPENDENT_NO_ASSIGNMENT, ITERATION_1PLUS, this::analyseIndependentNoAssignment)
                    .add(ANALYSE_CONTAINER_NO_ASSIGNMENT, ITERATION_1PLUS, this::analyseContainerNoAssignment)
                    .add("followExtImm", this::followExternalImmutable)
                    .build();

    private AnalysisStatus analyseFirstIteration(SharedState sharedState) {
        assert sharedState.iteration == 0;

        // parameters have EXTERNAL_IGNORE_MODIFICATIONS set to not-involved
        // (there is no intention to implement a feed-back from field to parameter)
        if (!parameterAnalysis.properties.isDone(EXTERNAL_IGNORE_MODIFICATIONS)) {
            parameterAnalysis.setProperty(EXTERNAL_IGNORE_MODIFICATIONS, EXTERNAL_IGNORE_MODIFICATIONS.valueWhenAbsent());
        }

        if (parameterInfo.parameterizedType.isPrimitiveExcludingVoid() &&
                !parameterAnalysis.properties.isDone(Property.MODIFIED_OUTSIDE_METHOD)) {
            parameterAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.FALSE_DV);
        }

        // NOTE: a shortcut on immutable to set modification to false is not possible because of casts, see Cast_1
        // NOTE: contractImmutable only has this meaning in iteration 0; once the other two components have been
        // computed, the property IMMUTABLE is not "contract" anymore
        DV formallyImmutable = analyserContext.defaultImmutable(parameterInfo.parameterizedType, false);
        DV contractBefore = parameterAnalysis.getProperty(IMMUTABLE_BEFORE_CONTRACTED);
        DV contractImmutable = parameterAnalysis.getProperty(IMMUTABLE);
        if ((contractImmutable.isDone() || contractBefore.isDone()) && formallyImmutable.isDone()
                && !parameterAnalysis.properties.isDone(IMMUTABLE)) {
            DV combined = combineImmutable(formallyImmutable, contractImmutable, contractBefore.valueIsTrue());
            assert combined.isDone();
            parameterAnalysis.setProperty(IMMUTABLE, combined);
        }

        DV contractIndependent = parameterAnalysis.getProperty(INDEPENDENT);
        if (contractIndependent.isDone() && !parameterAnalysis.properties.isDone(INDEPENDENT)) {
            parameterAnalysis.setProperty(INDEPENDENT, contractIndependent);
        }

        DV contractContainer = parameterAnalysis.getProperty(CONTAINER);
        if (!parameterAnalysis.properties.isDone(CONTAINER)) {
            if (contractContainer.isDone()) {
                parameterAnalysis.setProperty(CONTAINER, contractContainer);
                parameterAnalysis.setProperty(EXTERNAL_CONTAINER, contractContainer);
            } else {
                // essentially here to satisfy the internal check that java.lang.String is always a @Container
                // returns a non-null in some standard situations (array, unbound pt, final type)
                DV dv = analyserContext.safeContainer(parameterInfo.parameterizedType);
                if (dv != null) {
                    parameterAnalysis.setProperty(CONTAINER, dv);
                    parameterAnalysis.setProperty(EXTERNAL_CONTAINER, dv);
                }
            }
        }


        DV contractModified = parameterAnalysis.getProperty(Property.MODIFIED_VARIABLE);
        if (contractModified.isDone() && !parameterAnalysis.properties.isDone(Property.MODIFIED_OUTSIDE_METHOD)) {
            parameterAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, contractModified);
        }

        if (parameterInfo.parameterizedType.isPrimitiveExcludingVoid()) {
            if (!parameterAnalysis.properties.isDone(Property.EXTERNAL_NOT_NULL)) {
                parameterAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, NOT_INVOLVED_DV); // not involved
            }
        } else {
            DV contractNotNull = parameterAnalysis.getProperty(Property.NOT_NULL_PARAMETER);
            if (contractNotNull.isDone() && !parameterAnalysis.properties.isDone(Property.EXTERNAL_NOT_NULL)) {
                parameterAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, contractNotNull);
            }
        }

        // implicit @IgnoreModifications rule for java.util.function
        if (parameterAnalysis.getPropertyFromMapDelayWhenAbsent(IGNORE_MODIFICATIONS).isDelayed()) {
            boolean ignore = parameterInfo.parameterizedType.isAbstractInJavaUtilFunction(analyserContext)
                    && !parameterInfo.getMethod().isPrivate();
            parameterAnalysis.setProperty(IGNORE_MODIFICATIONS, ignore ? IGNORE_MODS_DV : NOT_IGNORE_MODS_DV);
        }

        if (parameterAnalysis.getProperty(MODIFIED_VARIABLE).isDelayed()) {
            DV contractIgnoreMod = parameterAnalysis.getPropertyFromMapDelayWhenAbsent(IGNORE_MODIFICATIONS);
            if (contractIgnoreMod.equals(IGNORE_MODS_DV)) {
                parameterAnalysis.setProperty(MODIFIED_VARIABLE, DV.FALSE_DV);
            }
        }

        if (isNoFieldsInvolved()) {
            noFieldsInvolved();
        }

        return DONE;
    }

    // See e.g. Enum_1: at some point, if we have a parameter of our own type, we may obtain a safe value
    private AnalysisStatus analyseContainerNoAssignment(SharedState sharedState) {
        if (parameterAnalysis.properties.isDone(CONTAINER)) return DONE;
        if (sharedState.iteration == 0) {
            // not setting assigned to field here
            return parameterInfo.delay(CauseOfDelay.Cause.ASSIGNED_TO_FIELD);
        }
        if (!parameterAnalysis.isAssignedToFieldDelaysResolved()) {
            // we wait until the other analyser has finished, since we need the properties it computes
            return parameterInfo.delay(CauseOfDelay.Cause.ASSIGNED_TO_FIELD);
        }
        ParameterizedType type = parameterInfo.parameterizedType;
        DV safe = analyserContext.safeContainer(type);
        if (safe != null) {
            if (safe.isDone()) {
                parameterAnalysis.setProperty(CONTAINER, safe);
                return DONE;
            }
            return safe.causesOfDelay();
        }
        // for our own type, we don't have to use "safe", because we can see everything
        DV formal = analyserContext.defaultContainer(type);
        if (type.typeInfo == parameterInfo.getOwningType()) {
            if (formal.isDone()) {
                parameterAnalysis.setProperty(CONTAINER, formal);
                return DONE;
            }
        }
        DV context = parameterAnalysis.getProperty(CONTEXT_CONTAINER);
        DV external = parameterAnalysis.getProperty(EXTERNAL_CONTAINER);
        DV best = context.max(external).max(formal);
        parameterAnalysis.setProperty(CONTAINER, best);
        return AnalysisStatus.of(best);
    }

    private AnalysisStatus analyseIndependentNoAssignment(SharedState sharedState) {
        if (parameterAnalysis.properties.isDone(INDEPENDENT)) return DONE;

        // in the first iteration, no statements have been analysed yet
        if (sharedState.iteration == 0) {
            CausesOfDelay delay = parameterInfo.delay(CauseOfDelay.Cause.ASSIGNED_TO_FIELD);
            parameterAnalysis.setProperty(INDEPENDENT, delay);
            return delay;
        }

        // we're not only dealing with "noFieldsInvolved", but actually also with constructors

        if (!parameterAnalysis.isAssignedToFieldDelaysResolved()) {
            // we wait until the other analyser has finished, since we need the properties it computes
            return parameterInfo.delay(CauseOfDelay.Cause.ASSIGNED_TO_FIELD);
        }
        /*
         Because INDEPENDENT has not been set by ANALYSE_FIELD_ASSIGNMENTS, it cannot have been assigned to a field.
         We can still encounter the following situations:
         - hidden content leaks out if this parameter is of the correct type (e.g., @FunctionalInterface) and
           a modifying method of the parameter has been called. We take the parameter as variable in the last statement
           of the method, and look at the linked1variables. If not empty, we can assign something lower than INDEPENDENT.
         - the method is modifying (constructor, explicitly computed): again, look at linked1
         */

        StatementAnalysis lastStatement = analyserContext.getMethodAnalysis(parameterInfo.owner).getLastStatement();
        if (lastStatement != null) {
            VariableInfo vi = lastStatement.findOrNull(parameterInfo, Stage.MERGE);
            if (vi != null) {
                if (!vi.linkedVariablesIsSet()) {
                    LOGGER.debug("Delay independent in parameter {}, waiting for linked1variables in statement {}",
                            parameterInfo.fullyQualifiedName(), lastStatement.index());
                    return new SimpleSet(new VariableCause(parameterInfo, lastStatement.location(Stage.MERGE), CauseOfDelay.Cause.LINKING));
                }
                List<FieldReference> fields = vi.getLinkedVariables().variables().entrySet().stream()
                        .filter(e -> e.getKey() instanceof FieldReference && e.getValue().ge(LinkedVariables.INDEPENDENT1_DV))
                        .map(e -> (FieldReference) e.getKey()).toList();
                if (!fields.isEmpty()) {
                    // so we know the parameter is content linked to some fields
                    // now the value of independence (from 1 to infinity) is determined by the size of the
                    // hidden content component inside the field

                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(parameterInfo.owner.typeInfo);

                    CausesOfDelay hiddenContentDelayed = fields.stream()
                            .map(fr -> typeAnalysis.hiddenContentTypeStatus())
                            .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
                    if (hiddenContentDelayed.isDelayed()) {
                        LOGGER.debug("Delay independent in parameter {}, waiting for hidden content/transparent types",
                                parameterInfo.fullyQualifiedName());
                        return hiddenContentDelayed;
                    }
                    DV minHiddenContentImmutable = fields.stream()
                            // hidden content is available, because linking has been computed(?)
                            .flatMap(fr -> typeAnalysis.hiddenContentLinkedTo(fr.fieldInfo).stream())
                            .map(pt -> analyserContext.defaultImmutable(pt, false))
                            .reduce(EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV::min);
                    if (minHiddenContentImmutable.isDelayed()) {
                        LOGGER.debug("Delay independent in parameter {}, waiting for immutable of hidden content/transparent types",
                                parameterInfo.fullyQualifiedName());
                        return minHiddenContentImmutable.causesOfDelay();
                    }
                    int immutableLevel = MultiLevel.level(minHiddenContentImmutable);
                    DV independent = immutableLevel <= MultiLevel.Level.IMMUTABLE_2.level ? INDEPENDENT_1_DV :
                            MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
                    LOGGER.debug("Assign {} to parameter {}", independent, parameterInfo.fullyQualifiedName());
                    parameterAnalysis.setProperty(INDEPENDENT, independent);
                    return DONE;
                }
            }
        }
        // finally, no other alternative
        parameterAnalysis.setProperty(INDEPENDENT, INDEPENDENT_DV);
        return DONE;
    }

    /**
     * Copy properties from an effectively final field  (FINAL=Level.TRUE) to the parameter that is is assigned to.
     * Does not apply to variable fields.
     */
    @Override
    public AnalyserResult analyse(int iteration) {
        try {
            AnalysisStatus analysisStatus = analyserComponents.run(new SharedState(iteration));
            analyserResultBuilder.setAnalysisStatus(analysisStatus);
            return analyserResultBuilder.build();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in parameter analyser, {}", parameterInfo.newLocation());
            throw rte;
        }
    }

    private static final Set<Property> PROPERTIES = Set.of(EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD,
            EXTERNAL_IMMUTABLE, EXTERNAL_CONTAINER); // For now, NOT going with EXTERNAL_IGNORE_MODS

    private static Set<Property> propertiesToCopy(DV assignedOrLinked) {
        if (LinkedVariables.isAssigned(assignedOrLinked)) return PROPERTIES;
        if (assignedOrLinked.equals(LinkedVariables.DEPENDENT_DV)) return Set.of(MODIFIED_OUTSIDE_METHOD);
        return Set.of();
    }

    private AnalysisStatus analyseFieldAssignments(SharedState sharedState) {
        boolean changed = false;

        if (!parameterAnalysis.assignedToFieldIsFrozen()) {
            // no point, we need to have seen the statement+field analysers first.
            if (sharedState.iteration == 0) {
                CausesOfDelay delay = parameterInfo.delay(CauseOfDelay.Cause.ASSIGNED_TO_FIELD);
                parameterAnalysis.setCausesOfAssignedToFieldDelays(delay);
                return delay;
            }

            StatementAnalysis lastStatementAnalysis = analyserContext.getMethodAnalysis(parameterInfo.owner)
                    .getLastStatement();
            Set<FieldInfo> fieldsAssignedInThisMethod =
                    ResolverImpl.accessibleFieldsStream(analyserContext, parameterInfo.owner.typeInfo,
                                    parameterInfo.owner.typeInfo.primaryType())
                            .filter(fieldInfo -> isAssignedIn(lastStatementAnalysis, fieldInfo))
                            .collect(Collectors.toSet());

            // find a field that's linked to me; bail out when not all field's values are set.
            CausesOfDelay delays = CausesOfDelay.EMPTY;
            for (FieldInfo fieldInfo : fieldsAssignedInThisMethod) {
                FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldInfo);
                DV assignedOrLinked = determineAssignedOrLinked(fieldAnalysis);
                if (assignedOrLinked.isDelayed()) {
                    delays = delays.merge(assignedOrLinked.causesOfDelay());
                } else if (parameterAnalysis.addAssignedToField(fieldInfo, assignedOrLinked)) {
                    changed |= LinkedVariables.isAssignedOrLinked(assignedOrLinked);
                }
            }

            if (delays.isDelayed()) {
                return delays.addProgress(changed);
            }
            parameterAnalysis.freezeAssignedToField();
        }

        Map<FieldInfo, DV> map = parameterAnalysis.getAssignedToField();
        CausesOfDelay delays = CausesOfDelay.EMPTY;

        Set<Property> propertiesDelayed = new HashSet<>();
        for (Map.Entry<FieldInfo, DV> e : map.entrySet()) {
            FieldInfo fieldInfo = e.getKey();
            DV assignedOrLinked = e.getValue();
            Set<Property> propertiesToCopy = propertiesToCopy(assignedOrLinked);
            FieldAnalyser fieldAnalyser = fieldAnalysers.get(fieldInfo);
            if (fieldAnalyser != null) {
                FieldAnalysis fieldAnalysis = fieldAnalyser.getFieldAnalysis();

                for (Property property : propertiesToCopy) {
                    DV inField = fieldAnalysis.getProperty(property);
                    if (inField.isDone()) {
                        if (!parameterAnalysis.properties.isDone(property)) {
                            LOGGER.debug("Copying value {} from field {} to parameter {} for property {}", inField,
                                    fieldInfo.fullyQualifiedName(), parameterInfo.fullyQualifiedName(), property);
                            parameterAnalysis.setProperty(property, inField);
                            changed = true;
                        }
                    } else {
                        propertiesDelayed.add(property);
                        LOGGER.debug("Still delaying copiedFromFieldToParameters because of {}, field {} ~ param {}",
                                property, fieldInfo.name, parameterInfo.name);
                        delays = delays.merge(inField.causesOfDelay());
                        if (property == MODIFIED_OUTSIDE_METHOD) {
                            // what if I'm the cause of the MOM delay? I need that data to progress!
                            // tell whoever generates a CM delay based on me that they can skip. See ComputeLinkedVariables
                            Stream<CauseOfDelay> vcs = findModifiedOutsideMethod(inField.causesOfDelay());
                            if (vcs.findAny().isPresent()) {
                                CausesOfDelay breakDelay = parameterInfo.delay(CauseOfDelay.Cause.BREAK_MOM_DELAY);
                                delays = delays.merge(breakDelay);
                                // let's not forget to set the delay, so that the statement analyser picks it up
                                parameterAnalysis.setProperty(property, delays);
                            }
                        }
                    }
                }

                if (!parameterAnalysis.properties.isDone(INDEPENDENT) && (LinkedVariables.isNotIndependent(assignedOrLinked))) {
                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(parameterInfo.owner.typeInfo);
                    if (typeAnalysis.hiddenContentTypeStatus().isDone()) {
                        SetOfTypes transparent = typeAnalysis.getTransparentTypes();
                        if (transparent.contains(parameterInfo.parameterizedType)) {
                            parameterAnalysis.setProperty(INDEPENDENT, INDEPENDENT_1_DV);
                            LOGGER.debug("Set parameter to @Independent1: {} because transparent and linked/assigned to field {}",
                                    parameterInfo.fullyQualifiedName(), fieldInfo.name);
                            changed = true;
                        } else {
                            DV immutable = analyserContext.defaultImmutable(parameterInfo.parameterizedType, false);
                            if (immutable.isDelayed()) {
                                delays = delays.merge(immutable.causesOfDelay());
                            } else {
                                int levelImmutable = MultiLevel.level(immutable);
                                DV typeIndependent;
                                if (levelImmutable <= MultiLevel.Level.IMMUTABLE_1.level) {
                                    if (assignedOrLinked.le(LinkedVariables.DEPENDENT_DV)) {
                                        typeIndependent = DEPENDENT_DV;
                                    } else {
                                        typeIndependent = INDEPENDENT_1_DV;
                                    }
                                } else {
                                    typeIndependent = MultiLevel.independentCorrespondingToImmutableLevelDv(levelImmutable);
                                }
                                parameterAnalysis.setProperty(INDEPENDENT, typeIndependent);
                                LOGGER.debug("Set @Dependent on parameter {}: linked/assigned to field {}",
                                        parameterInfo.fullyQualifiedName(), fieldInfo.name);
                                changed = true;
                            }
                        }
                    } else delays = delays.merge(typeAnalysis.hiddenContentTypeStatus());
                }
            }
        }


        for (Property property : PROPERTIES) {
            if (!parameterAnalysis.properties.isDone(property) && !propertiesDelayed.contains(property)) {
                DV v;
                if (property == EXTERNAL_CONTAINER || property == EXTERNAL_NOT_NULL || property == EXTERNAL_IGNORE_MODIFICATIONS) {
                    v = property.valueWhenAbsent();
                } else if (property == EXTERNAL_IMMUTABLE) {
                    v = analyserContext.defaultImmutable(parameterInfo.parameterizedType, false);
                    // do not let this stop "resolve field delays"; we're not linked to a field so no worries
                    // followExternalImmutable will ensure that a value is given at some point
                } else {
                    v = property.falseDv;
                }
                parameterAnalysis.setProperty(property, v);
                LOGGER.debug("Wrote false to parameter {} for property {}", parameterInfo.fullyQualifiedName(),
                        property);
                changed = true;
            }
        }

        assert delays.isDelayed() || parameterAnalysis.properties.isDone(Property.MODIFIED_OUTSIDE_METHOD) &&
                parameterAnalysis.properties.isDone(Property.EXTERNAL_NOT_NULL) &&
                //   parameterAnalysis.properties.isDone(EXTERNAL_IMMUTABLE) &&
                parameterAnalysis.properties.isDone(EXTERNAL_CONTAINER);

        if (delays.isDelayed()) {
            parameterAnalysis.setCausesOfAssignedToFieldDelays(delays);
            return delays.addProgress(changed);
        }

        // can be executed multiple times
        parameterAnalysis.resolveFieldDelays();
        return DONE;
    }

    private AnalysisStatus followExternalImmutable(SharedState sharedState) {
        DV extImm = parameterAnalysis.getProperty(EXTERNAL_IMMUTABLE);
        if (extImm.isDone()) {
            return DONE;
        }
        DV dv = analyserContext.defaultImmutable(parameterInfo.parameterizedType, false);
        parameterAnalysis.setProperty(EXTERNAL_IMMUTABLE, dv);
        return AnalysisStatus.of(dv);
    }

    private Stream<CauseOfDelay> findModifiedOutsideMethod(CausesOfDelay causes) {
        return causes.causesStream().filter(c -> c.cause() == CauseOfDelay.Cause.MODIFIED_OUTSIDE_METHOD)
                .filter(c -> c instanceof VariableCause v && v.variable() == parameterInfo ||
                        c instanceof SimpleCause sc && sc.location().getInfo() == parameterInfo);
    }

    // if contractImmutable is delayed, then @BeforeMark is present
    private DV combineImmutable(DV formallyImmutable, DV contractImmutable, boolean contractedBefore) {
        assert contractImmutable.isDone() || contractedBefore;
        assert formallyImmutable.isDone();

        int formalLevel = MultiLevel.level(formallyImmutable);
        int contractLevel = contractImmutable.isDelayed() ? formalLevel : MultiLevel.level(contractImmutable);
        Effective formalEffective = MultiLevel.effective(formallyImmutable);
        Effective contractEffective = contractImmutable.isDelayed() ? formalEffective : MultiLevel.effective(contractImmutable);

        if (contractedBefore) {
            if (contractEffective == EFFECTIVE || formalEffective == EVENTUAL) {
                if (formalEffective != EVENTUAL || contractLevel != formalLevel) {
                    analyserResultBuilder.add(Message.newMessage(parameterAnalysis.location,
                            Message.Label.INCOMPATIBLE_IMMUTABILITY_CONTRACT_BEFORE));
                    return formallyImmutable;
                }
                return MultiLevel.composeImmutable(EVENTUAL_BEFORE, contractLevel);
            }
            analyserResultBuilder.add(Message.newMessage(parameterAnalysis.location,
                    Message.Label.INCOMPATIBLE_IMMUTABILITY_CONTRACT_BEFORE_NOT_EVENTUALLY_IMMUTABLE));
            return formallyImmutable;
        }

        if (contractEffective == EVENTUAL_AFTER && formalEffective == EVENTUAL && contractLevel == formalLevel) {
            return contractImmutable;
        }

        if (contractEffective == EFFECTIVE) {
            if (formalEffective != EVENTUAL && formalEffective != EFFECTIVE ||
                    contractLevel != formalLevel) {
                analyserResultBuilder.add(Message.newMessage(parameterAnalysis.location,
                        Message.Label.INCOMPATIBLE_IMMUTABILITY_CONTRACT_AFTER));
                return formallyImmutable;
            }
            return formalEffective == EVENTUAL ? MultiLevel.composeImmutable(EVENTUAL_AFTER, contractLevel) : contractImmutable;
        }

        if (contractImmutable.equals(MUTABLE_DV)) {
            return formallyImmutable;
        }
        throw new UnsupportedOperationException("Should have covered all the bases");
    }

    private boolean isNoFieldsInvolved() {
        boolean methodIsStatic = parameterInfo.owner.methodInspection.get().isStatic();
        return parameterInfo.owner.typeInfo.typeInspection.get().fields().stream()
                .filter(fieldInfo -> !methodIsStatic || fieldInfo.isStatic())
                .allMatch(fieldInfo -> fieldInfo.isExplicitlyFinal() && !parameterInfo.owner.isConstructor);
    }

    private void noFieldsInvolved() {
        for (Property property : PROPERTIES) {
            if (!parameterAnalysis.properties.isDone(property)) {
                parameterAnalysis.setProperty(property, property.valueWhenAbsent());
            }
        }
        parameterAnalysis.freezeAssignedToField();
        parameterAnalysis.resolveFieldDelays();
    }

    private boolean isAssignedIn(StatementAnalysis lastStatementAnalysis, FieldInfo fieldInfo) {
        VariableInfo vi = lastStatementAnalysis.findOrNull(new FieldReference(analyserContext, fieldInfo),
                Stage.MERGE);
        return vi != null && vi.isAssigned();
    }

    private DV determineAssignedOrLinked(FieldAnalysis fieldAnalysis) {
        DV effFinal = fieldAnalysis.getProperty(Property.FINAL);
        if (effFinal.isDelayed()) {
            return effFinal;
        }
        if (effFinal.valueIsTrue()) {
            Expression effectivelyFinal = fieldAnalysis.getValue();
            if (effectivelyFinal.isDelayed()) {
                return effectivelyFinal.causesOfDelay();
            }
            VariableExpression ve;

            // == parameterInfo works fine unless a super(...) has been used
            if ((ve = effectivelyFinal.asInstanceOf(VariableExpression.class)) != null && ve.variable() == parameterInfo) {
                return ASSIGNED_DV;
            }
            // the case of multiple constructors
            if (effectivelyFinal instanceof MultiValue multiValue &&
                    Arrays.stream(multiValue.multiExpression.expressions())
                            .anyMatch(e -> {
                                VariableExpression ve2;
                                return (ve2 = e.asInstanceOf(VariableExpression.class)) != null
                                        && ve2.variable() == parameterInfo;
                            })) {
                return ASSIGNED_DV;
            }
            // the case of this(...) or super(...)
            StatementAnalysis firstStatement = analyserContext.getMethodAnalysis(parameterInfo.owner).getFirstStatement();
            if (ve != null && ve.variable() instanceof ParameterInfo pi &&
                    firstStatement != null && firstStatement.statement() instanceof ExplicitConstructorInvocation eci &&
                    eci.methodInfo == pi.owner) {
                Expression param = eci.structure.updaters().get(pi.index);
                VariableExpression ve2;
                if ((ve2 = param.asInstanceOf(VariableExpression.class)) != null && ve2.variable() == parameterInfo) {
                    return ASSIGNED_DV;
                }
            }
        }

        // variable field, no direct assignment to parameter
        LinkedVariables linked = fieldAnalysis.getLinkedVariables();
        return linked.variables().getOrDefault(parameterInfo, LinkedVariables.NO_LINKING_DV);
    }

    public static final Property[] CONTEXT_PROPERTIES = {Property.CONTEXT_NOT_NULL,
            Property.CONTEXT_MODIFIED, CONTEXT_IMMUTABLE, CONTEXT_CONTAINER};

    private AnalysisStatus analyseContext(SharedState sharedState) {
        // no point, we need to have seen the statement+field analysers first.
        if (sharedState.iteration == 0) {
            return parameterInfo.delay(CauseOfDelay.Cause.FIRST_ITERATION);
        }

        // context not null, context modified
        MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(parameterInfo.owner);
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        VariableInfo vi = lastStatement.getLatestVariableInfo(parameterInfo.fullyQualifiedName());
        CausesOfDelay delayFromContext = CausesOfDelay.EMPTY;
        boolean changed = false;
        for (Property property : CONTEXT_PROPERTIES) {
            if (!parameterAnalysis.properties.isDone(property)) {
                DV value = vi.getProperty(property);
                if (value.isDone()) {
                    parameterAnalysis.setProperty(property, value);
                    LOGGER.debug("Set {} on parameter {} to {}", property,
                            parameterInfo.fullyQualifiedName(), value);
                    changed = true;
                } else {
                    LOGGER.debug(
                            "Delays on {} not yet resolved for parameter {}, delaying", property,
                            parameterInfo.fullyQualifiedName());
                    delayFromContext = delayFromContext.merge(value.causesOfDelay());
                }
            }
        }
        if (delayFromContext.isDelayed()) {
            return delayFromContext.causesOfDelay().addProgress(changed);
        }
        return DONE;
    }

    private AnalysisStatus checkUnusedParameter(SharedState sharedState) {
        // no point, we need to have seen the statement+field analysers first.
        if (sharedState.iteration == 0) {
            return parameterInfo.delay(CauseOfDelay.Cause.FIRST_ITERATION);
        }

        StatementAnalysis lastStatementAnalysis = analyserContext.getMethodAnalysis(parameterInfo.owner)
                .getLastStatement();
        VariableInfo vi = lastStatementAnalysis == null ? null :
                lastStatementAnalysis.findOrNull(parameterInfo, Stage.MERGE);
        if (vi == null || !vi.isRead()) {
            boolean takeValueFromOverride;
            if (lastStatementAnalysis != null && parameterInfo.owner.isNotOverridingAnyOtherMethod()
                    && !parameterInfo.owner.isCompanionMethod()) {
                analyserResultBuilder.add(Message.newMessage(parameterInfo.owner.newLocation(),
                        Message.Label.UNUSED_PARAMETER, parameterInfo.simpleName()));
                takeValueFromOverride = false;
            } else {
                // so that there are no complaints
                takeValueFromOverride = true;
            }

            // unused variable
            if (!parameterAnalysis.properties.isDone(Property.MODIFIED_OUTSIDE_METHOD)) {
                parameterAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.FALSE_DV);
            }
            parameterAnalysis.setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);

            // @NotNull
            if (!parameterAnalysis.properties.isDone(Property.EXTERNAL_NOT_NULL)) {
                parameterAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, NOT_INVOLVED_DV);
            }
            parameterAnalysis.setProperty(Property.CONTEXT_NOT_NULL, NULLABLE_DV);

            // @Container: handled separately

            // @IgnoreModifications
            parameterAnalysis.setProperty(IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv);

            // @Independent
            if (!parameterAnalysis.properties.isDone(INDEPENDENT)) {
                DV independent;
                if (takeValueFromOverride) {
                    independent = computeValueFromOverrides(INDEPENDENT);
                } else {
                    independent = NOT_INVOLVED_DV;
                }
                parameterAnalysis.setProperty(INDEPENDENT, independent);
            }

            if (!parameterAnalysis.properties.isDone(EXTERNAL_IMMUTABLE)) {
                parameterAnalysis.setProperty(EXTERNAL_IMMUTABLE, EXTERNAL_IMMUTABLE.valueWhenAbsent());
            }
            if (!parameterAnalysis.properties.isDone(EXTERNAL_CONTAINER)) {
                parameterAnalysis.setProperty(EXTERNAL_CONTAINER, EXTERNAL_CONTAINER.valueWhenAbsent());
            }
            parameterAnalysis.setProperty(CONTEXT_IMMUTABLE, MUTABLE_DV);

            parameterAnalysis.resolveFieldDelays();
            return DONE_ALL; // no point visiting any of the other analysers
        }
        return DONE;
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        return null;
    }
}
