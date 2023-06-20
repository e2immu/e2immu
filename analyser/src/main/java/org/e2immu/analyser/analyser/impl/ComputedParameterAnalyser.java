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
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analyser.util.ComputeIndependent;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MultiValue;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExplicitConstructorInvocation;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.resolver.impl.ResolverImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE_ALL;
import static org.e2immu.analyser.analyser.LinkedVariables.LINK_ASSIGNED;
import static org.e2immu.analyser.analyser.Property.*;
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
    public static final String ANALYSE_INDEPENDENT_OF_RETURN_VALUE = "PA:independentOfReturnValue";

    private Map<FieldInfo, FieldAnalyser> fieldAnalysers;

    public ComputedParameterAnalyser(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
        super(analyserContext, parameterInfo);
        AnalyserComponents.Builder<String, SharedState> ac = new AnalyserComponents.Builder<String, SharedState>()
                .add(CHECK_UNUSED_PARAMETER, this::checkUnusedParameter)
                .add(ANALYSE_FIRST_ITERATION, this::analyseFirstIteration)
                .add(ANALYSE_CONTEXT, this::analyseContext);
        if (parameterInfo.owner.methodInspection.get().isFactoryMethod()) {
            ac.add(ANALYSE_INDEPENDENT_OF_RETURN_VALUE, this::analyseIndependentOfReturnValue);
        } else {
            ac.add(ANALYSE_FIELD_ASSIGNMENTS, this::analyseFieldAssignments)
                    .add(ANALYSE_INDEPENDENT_NO_ASSIGNMENT, this::analyseIndependentNoAssignment);
        }
        ac.add(ANALYSE_CONTAINER_NO_ASSIGNMENT, this::analyseContainerNoAssignment)
                .add("followExtImm", this::followExternalImmutable);
        analyserComponents = ac.build();

    }

    @Override
    public void initialize(Stream<FieldAnalyser> fieldAnalyserStream) {
        this.fieldAnalysers = fieldAnalyserStream.collect(Collectors.toUnmodifiableMap(FieldAnalyser::getFieldInfo, fa -> fa));
    }

    @Override
    public String fullyQualifiedAnalyserName() {
        return "CPA " + parameterInfo.fullyQualifiedName;
    }


    private final AnalyserComponents<String, SharedState> analyserComponents;


    private AnalysisStatus analyseFirstIteration(SharedState sharedState) {
        assert sharedState.iteration() == 0;

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
        DV formallyImmutable = analyserContext.typeImmutable(parameterInfo.parameterizedType);
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

        DV contractContainerRestriction = parameterAnalysis.getProperty(CONTAINER_RESTRICTION);
        if (!parameterAnalysis.properties.isDone(CONTAINER_RESTRICTION) && contractContainerRestriction.isDone()) {
            parameterAnalysis.setProperty(CONTAINER_RESTRICTION, contractContainerRestriction);
        }

        if (!parameterAnalysis.properties.isDone(CONTAINER)) {
            DV safeContainer = analyserContext.safeContainer(parameterInfo.parameterizedType);
            if (safeContainer != null && safeContainer.isDone()) {
                parameterAnalysis.setProperty(CONTAINER, safeContainer);
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
                    && !parameterInfo.getMethod().methodInspection.get().isPrivate();
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
        if (sharedState.iteration() == 0) {
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
        DV formal = analyserContext.typeContainer(type);
        if (type.typeInfo == parameterInfo.getOwningType()) {
            if (formal.isDone()) {
                parameterAnalysis.setProperty(CONTAINER, formal);
                return DONE;
            }
        }
        DV context = parameterAnalysis.getProperty(CONTEXT_CONTAINER);
        DV external = parameterAnalysis.getProperty(CONTAINER_RESTRICTION);
        DV best = context.max(external).max(formal);
        parameterAnalysis.setProperty(CONTAINER, best);
        return AnalysisStatus.of(best);
    }

    private AnalysisStatus analyseIndependentNoAssignment(SharedState sharedState) {
        if (parameterAnalysis.properties.isDone(INDEPENDENT)) return DONE;
        DV immutable = analyserContext.typeImmutable(parameterInfo.parameterizedType);

        // there is no restriction on immutable, because the link could have been STATICALLY_ASSIGNED
        if (EFFECTIVELY_IMMUTABLE_DV.equals(immutable)) {
            LOGGER.debug("Assign INDEPENDENT to parameter {}: type is recursively immutable", parameterInfo);
            parameterAnalysis.setProperty(INDEPENDENT, INDEPENDENT_DV);
            return DONE;
        }

        // in the first iteration, no statements have been analysed yet
        if (sharedState.iteration() == 0) {
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
           of the method, and look at the linked variables. If not empty, we can assign something lower than INDEPENDENT.
         - the method is modifying (constructor, explicitly computed): again, look at linked variables
         */

        StatementAnalysis lastStatement = analyserContext.getMethodAnalysis(parameterInfo.owner).getLastStatement();
        DV independent = INDEPENDENT_DV;
        CausesOfDelay delay = CausesOfDelay.EMPTY;

        if (lastStatement != null) {

            // from the parameter to one or more fields
            VariableInfo viParam = lastStatement.findOrNull(parameterInfo, Stage.MERGE);
            if (viParam != null) {
                if (!viParam.linkedVariablesIsSet()) {
                    if (sharedState.breakDelayLevel().acceptParameter() && viParam.getLinkedVariables().causesOfDelay()
                            .containsCauseOfDelay(CauseOfDelay.Cause.LINKING)) {
                        LOGGER.debug("Breaking parameter delay in independent, parameter {}", parameterInfo.fullyQualifiedName);
                        parameterAnalysis.setProperty(INDEPENDENT, INDEPENDENT_DV);
                        return DONE;
                    }
                    LOGGER.debug("Delay independent in parameter {}, waiting for linked1variables in statement {}",
                            parameterInfo.fullyQualifiedName(), lastStatement.index());
                    delay = DelayFactory.createDelay(new VariableCause(parameterInfo, lastStatement.location(Stage.MERGE),
                            CauseOfDelay.Cause.LINKING));
                } else {
                    Map<Variable, DV> fields = viParam.getLinkedVariables().variables().entrySet().stream()
                            .filter(e -> e.getKey() instanceof FieldReference fr && fr.scopeIsThis() || e.getKey() instanceof This)
                            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
                    if (!fields.isEmpty()) {
                        /*
                         The parameter is linked to some fields, or to "this", because of expanded variables,
                         see e.g. E2ImmutableComposition_0.EncapsulatedExposedArrayOfHasSize)
                         */
                        DV dv = independentFromFields(immutable, fields);
                        if (dv.isDelayed()) {
                            delay = dv.causesOfDelay();
                        } else {
                            independent = dv;
                        }
                    }
                }
            }

            // from fields to parameters, exposing via a functional interface (e.g. Independent1_0)
            List<VariableInfo> vis = lastStatement.variableStream()
                    .filter(v -> v.variable() instanceof FieldReference fr && fr.scopeIsThis())
                    .toList();
            for (VariableInfo vi : vis) {
                if (!vi.linkedVariablesIsSet()) {
                    if (sharedState.breakDelayLevel().acceptParameter() && vi.getLinkedVariables().causesOfDelay()
                            .containsCauseOfDelay(CauseOfDelay.Cause.LINKING)) {
                        LOGGER.debug("Breaking parameter delay in independent, parameter {}, field {}",
                                parameterInfo, vi.variable());
                        parameterAnalysis.setProperty(INDEPENDENT, INDEPENDENT_DV);
                        return DONE;
                    }
                    LOGGER.debug("Delay independent in parameter {}, waiting for linked1variables in statement {}",
                            parameterInfo.fullyQualifiedName(), lastStatement.index());
                    delay = delay.merge(DelayFactory.createDelay(new VariableCause(vi.variable(), lastStatement.location(Stage.MERGE),
                            CauseOfDelay.Cause.LINKING)));
                } else {
                    DV linkToParameter = vi.getLinkedVariables().value(parameterInfo);
                    if (linkToParameter != null) {
                        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(parameterInfo.getTypeInfo());
                        if (typeAnalysis.hiddenContentDelays().isDelayed()) {
                            return typeAnalysis.hiddenContentDelays().causesOfDelay();
                        }
                        SetOfTypes hiddenContentCurrentType = typeAnalysis.getHiddenContentTypes();
                        ComputeIndependent computeIndependent = new ComputeIndependent(analyserContext, hiddenContentCurrentType,
                                parameterInfo.getTypeInfo(), true);

                        DV independentOfParameter = computeIndependent.typesAtLinkLevel(linkToParameter,
                                parameterInfo.parameterizedType, immutable, vi.variable().parameterizedType());
                        independent = independent.min(independentOfParameter);
                    }
                }
            }
        }
        if (delay.isDelayed()) {
            LOGGER.debug("Delaying independent of parameter {}", parameterInfo);
            parameterAnalysis.setProperty(INDEPENDENT, delay);
            return AnalysisStatus.of(delay);
        }
        LOGGER.debug("Setting independent of parameter {} to {}", parameterInfo, independent);
        parameterAnalysis.setProperty(INDEPENDENT, independent);
        return AnalysisStatus.of(independent);
    }

    private DV independentFromFields(DV immutable, Map<Variable, DV> fields) {
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(parameterInfo.getTypeInfo());
        if (typeAnalysis.hiddenContentDelays().isDelayed()) {
            return typeAnalysis.hiddenContentDelays().causesOfDelay();
        }
        SetOfTypes hiddenContentCurrentType = typeAnalysis.getHiddenContentTypes();

        ComputeIndependent computeIndependent = new ComputeIndependent(analyserContext, hiddenContentCurrentType,
                parameterInfo.getTypeInfo(), true);
        DV independent = fields.entrySet().stream()
                .map(e -> computeIndependent.typesAtLinkLevel(e.getValue(), parameterInfo.parameterizedType, immutable,
                        e.getKey().parameterizedType()))
                .reduce(INDEPENDENT_DV, DV::min);
        LOGGER.debug("Assign {} to parameter {}", independent, parameterInfo);
        return independent;
    }

    private AnalysisStatus analyseIndependentOfReturnValue(SharedState sharedState) {
        assert parameterInfo.owner.hasReturnValue(); // factory method!
        if (!parameterAnalysis.isAssignedToFieldDelaysResolved()) parameterAnalysis.resolveFieldDelays();
        if (parameterAnalysis.properties.isDone(INDEPENDENT)) return DONE;
        DV immutable = analyserContext.typeImmutable(parameterInfo.parameterizedType);

        // there is no restriction on immutable, because the link could have been STATICALLY_ASSIGNED
        if (EFFECTIVELY_IMMUTABLE_DV.equals(immutable)) {
            LOGGER.debug("Assign INDEPENDENT to parameter {}: type is recursively immutable", parameterInfo);
            parameterAnalysis.setProperty(INDEPENDENT, INDEPENDENT_DV);
            return DONE;
        }
        // in the first iteration, no statements have been analysed yet
        if (sharedState.iteration() == 0) {
            CausesOfDelay delay = parameterInfo.delay(CauseOfDelay.Cause.ASSIGNED_TO_FIELD);
            parameterAnalysis.setProperty(INDEPENDENT, delay);
            return delay;
        }
        StatementAnalysis lastStatement = analyserContext.getMethodAnalysis(parameterInfo.owner).getLastStatement();
        DV independent = INDEPENDENT_DV;
        CausesOfDelay delay = CausesOfDelay.EMPTY;

        if (lastStatement != null) {
            VariableInfo rv = lastStatement.getLatestVariableInfo(parameterInfo.owner.fullyQualifiedName);
            assert rv != null && rv.variable() instanceof ReturnVariable;
            delay = rv.getLinkedVariables().causesOfDelay();
            Set<Variable> assigned = rv.getLinkedVariables().variablesAssigned().collect(Collectors.toUnmodifiableSet());
            VariableInfo pi = lastStatement.getLatestVariableInfo(parameterInfo.fullyQualifiedName);
            delay = delay.merge(pi.getLinkedVariables().causesOfDelay());
            if (delay.isDone()) {
                DV min = DV.MAX_INT_DV;
                for (Map.Entry<Variable, DV> e : pi.getLinkedVariables()) {
                    if (assigned.contains(e.getKey())) {
                        min = min.min(e.getValue());
                    }
                }
                if (min != DV.MAX_INT_DV) {
                    DV dv = independentFromFields(immutable, Map.of(rv.variable(), min));
                    if (dv.isDelayed()) {
                        delay = dv.causesOfDelay();
                    } else {
                        independent = dv;
                    }
                } // else: TODO NOT YET IMPLEMENTED
            }
        }
        if (delay.isDelayed()) {
            LOGGER.debug("Delaying independent wrt return value of parameter {}", parameterInfo);
            parameterAnalysis.setProperty(INDEPENDENT, delay);
            return AnalysisStatus.of(delay);
        }
        LOGGER.debug("Setting independent wrt return value of parameter {} to {}", parameterInfo, independent);
        parameterAnalysis.setProperty(INDEPENDENT, independent);
        return AnalysisStatus.of(independent);
    }

    /**
     * Copy properties from an effectively final field  (FINAL=Level.TRUE) to the parameter that it is assigned to.
     * Does not apply to variable fields.
     */
    @Override
    public AnalyserResult analyse(SharedState sharedState) {
        assert !isUnreachable();
        try {
            AnalysisStatus analysisStatus = analyserComponents.run(sharedState);
            if (analysisStatus.isDone()) parameterAnalysis.internalAllDoneCheck();
            analyserResultBuilder.setAnalysisStatus(analysisStatus);
            return analyserResultBuilder.build();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in parameter analyser, {}", parameterInfo.newLocation());
            throw rte;
        }
    }

    private static final List<Property> PROPERTY_LIST = List.of(EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD,
            EXTERNAL_IMMUTABLE, CONTAINER_RESTRICTION); // For now, NOT going with EXTERNAL_IGNORE_MODS

    private static List<Property> propertiesToCopy(DV assignedOrLinked) {
        if (LinkedVariables.isAssigned(assignedOrLinked)) return PROPERTY_LIST;
        if (assignedOrLinked.equals(LinkedVariables.LINK_DEPENDENT)) return List.of(MODIFIED_OUTSIDE_METHOD);
        return List.of();
    }

    private AnalysisStatus analyseFieldAssignments(SharedState sharedState) {
        boolean changed = false;

        if (!parameterAnalysis.assignedToFieldIsFrozen()) {
            // no point, we need to have seen the statement+field analysers first.
            if (sharedState.iteration() == 0) {
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
            List<Property> propertiesToCopy = propertiesToCopy(assignedOrLinked);
            FieldAnalyser fieldAnalyser = fieldAnalysers.get(fieldInfo);
            if (fieldAnalyser != null) {
                FieldAnalysis fieldAnalysis = fieldAnalyser.getFieldAnalysis();

                for (Property property : propertiesToCopy) {
                    if (!parameterAnalysis.properties.isDone(property)) {
                        DV inField = fieldAnalysis.getProperty(property);
                        if (inField.isDone()) {

                            LOGGER.debug("Copying value {} from field {} to parameter {} for property {}", inField,
                                    fieldInfo.fullyQualifiedName(), parameterInfo.fullyQualifiedName(), property);
                            parameterAnalysis.setProperty(property, inField);
                            changed = true;

                        } else {
                            propertiesDelayed.add(property);
                            LOGGER.debug("Still delaying copiedFromFieldToParameters because of {}, field {} ~ param {}",
                                    property, fieldInfo.name, parameterInfo.name);
                            delays = delays.merge(inField.causesOfDelay());
                            if (property == MODIFIED_OUTSIDE_METHOD) {
                                // what if I'm the cause of the MOM delay? I need that data to progress!
                                // tell whoever generates a CM delay based on me that they can skip. See ComputeLinkedVariables
                                boolean modifiedOutsideMethod = findModifiedOutsideMethod(inField.causesOfDelay());
                                if (modifiedOutsideMethod) {
                                    CausesOfDelay breakDelay = parameterInfo.delay(CauseOfDelay.Cause.BREAK_MOM_DELAY);
                                    delays = delays.merge(breakDelay);
                                    // let's not forget to set the delay, so that the statement analyser picks it up
                                    parameterAnalysis.setProperty(property, inField.causesOfDelay().merge(breakDelay));
                                } else {
                                    parameterAnalysis.setProperty(property, inField.causesOfDelay());
                                }
                            } else {
                                parameterAnalysis.setProperty(property, inField.causesOfDelay());
                            }
                        }
                    }
                }
            }
        }

        if (!parameterAnalysis.properties.isDone(INDEPENDENT)
                && !map.isEmpty()
                && map.values().stream().allMatch(LinkedVariables::isAssigned)) {
            DV immutable = analyserContext.typeImmutable(parameterInfo.parameterizedType);
            Map<Variable, DV> map2 = map.entrySet().stream().collect(Collectors
                    .toUnmodifiableMap(e -> new FieldReference(InspectionProvider.DEFAULT, e.getKey()), Map.Entry::getValue));
            DV independent = independentFromFields(immutable, map2);
            parameterAnalysis.setProperty(INDEPENDENT, independent);
            if (independent.isDelayed()) {
                LOGGER.debug("Delaying @Independent on parameter {}", parameterInfo);
                delays = delays.merge(independent.causesOfDelay());
            } else {
                LOGGER.debug("Set @Independent on parameter {}: linked/assigned to fields {}", parameterInfo, map.keySet());
                changed = true;
            }
        }

        for (Property property : PROPERTY_LIST) {
            if (!parameterAnalysis.properties.isDone(property) && !propertiesDelayed.contains(property)) {
                DV v;
                if (property == CONTAINER_RESTRICTION || property == EXTERNAL_NOT_NULL || property == EXTERNAL_IGNORE_MODIFICATIONS) {
                    v = property.valueWhenAbsent();
                } else if (property == EXTERNAL_IMMUTABLE) {
                    v = analyserContext.typeImmutable(parameterInfo.parameterizedType);
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
                parameterAnalysis.properties.isDone(CONTAINER_RESTRICTION);

        if (delays.isDelayed()) {
            parameterAnalysis.setCausesOfAssignedToFieldDelays(delays);
            LOGGER.debug("Delaying parameter analyser {}, delays: {}, progress? {}", parameterInfo.fullyQualifiedName,
                    delays, changed);
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
        if (parameterAnalysis.isAssignedToFieldDelaysResolved()) {
            DV dv = analyserContext.typeImmutable(parameterInfo.parameterizedType);
            parameterAnalysis.setProperty(EXTERNAL_IMMUTABLE, dv);
            return AnalysisStatus.of(dv);
        }
        return AnalysisStatus.of(parameterAnalysis.assignedToFieldDelays());
    }

    private boolean findModifiedOutsideMethod(CausesOfDelay causes) {
        return causes.containsCauseOfDelay(CauseOfDelay.Cause.MODIFIED_OUTSIDE_METHOD,
                c -> c instanceof VariableCause v && v.variable() == parameterInfo ||
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
                .filter(fieldInfo -> !methodIsStatic || fieldInfo.fieldInspection.get().isStatic())
                .allMatch(fieldInfo -> fieldInfo.isExplicitlyFinal() && !parameterInfo.owner.isConstructor);
    }

    private void noFieldsInvolved() {
        for (Property property : PROPERTY_LIST) {
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
                return LINK_ASSIGNED;
            }
            // the case of multiple constructors
            if (effectivelyFinal instanceof MultiValue multiValue &&
                    Arrays.stream(multiValue.multiExpression.expressions())
                            .anyMatch(e -> {
                                VariableExpression ve2;
                                return (ve2 = e.asInstanceOf(VariableExpression.class)) != null
                                        && ve2.variable() == parameterInfo;
                            })) {
                return LINK_ASSIGNED;
            }
            // the case of this(...) or super(...)
            StatementAnalysis firstStatement = analyserContext.getMethodAnalysis(parameterInfo.owner).getFirstStatement();
            if (ve != null && ve.variable() instanceof ParameterInfo pi &&
                    firstStatement != null && firstStatement.statement() instanceof ExplicitConstructorInvocation eci &&
                    eci.methodInfo == pi.owner) {
                Expression param = eci.structure.updaters().get(pi.index);
                VariableExpression ve2;
                if ((ve2 = param.asInstanceOf(VariableExpression.class)) != null && ve2.variable() == parameterInfo) {
                    return LINK_ASSIGNED;
                }
            }
        }

        // variable field, no direct assignment to parameter
        LinkedVariables linked = fieldAnalysis.getLinkedVariables();
        return linked.variables().getOrDefault(parameterInfo, LinkedVariables.LINK_INDEPENDENT);
    }

    public static final Property[] CONTEXT_PROPERTIES = {Property.CONTEXT_NOT_NULL,
            Property.CONTEXT_MODIFIED, CONTEXT_IMMUTABLE, CONTEXT_CONTAINER};

    private AnalysisStatus analyseContext(SharedState sharedState) {
        // no point, we need to have seen the statement+field analysers first.
        if (sharedState.iteration() == 0) {
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
                    LOGGER.debug("Set {} on parameter {} to {}", property, parameterInfo, value);
                    changed = true;
                } else {
                    LOGGER.debug("Delays on {} not yet resolved for parameter {}, delaying", property, parameterInfo);
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
        if (sharedState.iteration() == 0) {
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
            if (!parameterAnalysis.properties.isDone(IGNORE_MODIFICATIONS)) {
                parameterAnalysis.setProperty(IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv);
            }

            // @Independent
            if (!parameterAnalysis.properties.isDone(INDEPENDENT)) {
                DV independent;
                if (takeValueFromOverride) {
                    independent = computeValueFromOverrides(INDEPENDENT, false);
                } else {
                    independent = INDEPENDENT_DV;
                }
                parameterAnalysis.setProperty(INDEPENDENT, independent);
            }

            if (!parameterAnalysis.properties.isDone(EXTERNAL_IMMUTABLE)) {
                parameterAnalysis.setProperty(EXTERNAL_IMMUTABLE, EXTERNAL_IMMUTABLE.valueWhenAbsent());
            }
            if (!parameterAnalysis.properties.isDone(CONTAINER_RESTRICTION)) {
                parameterAnalysis.setProperty(CONTAINER_RESTRICTION, CONTAINER_RESTRICTION.valueWhenAbsent());
            }
            parameterAnalysis.setProperty(CONTEXT_IMMUTABLE, MUTABLE_DV);

            if (!parameterAnalysis.properties.isDone(CONTAINER)) {
                parameterAnalysis.setProperty(CONTAINER, CONTAINER.falseDv);
            }
            parameterAnalysis.setProperty(CONTEXT_CONTAINER, CONTAINER.falseDv);

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
