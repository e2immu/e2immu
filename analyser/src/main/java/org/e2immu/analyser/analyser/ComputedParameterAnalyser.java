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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MultiValue;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExplicitConstructorInvocation;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.resolver.Resolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE_ALL;
import static org.e2immu.analyser.analyser.LinkedVariables.ASSIGNED_DV;
import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.model.MultiLevel.*;
import static org.e2immu.analyser.model.MultiLevel.Effective.*;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public class ComputedParameterAnalyser extends ParameterAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputedParameterAnalyser.class);
    public static final String CHECK_UNUSED_PARAMETER = "checkUnusedParameter";
    public static final String ANALYSE_FIRST_ITERATION = "analyseFirstIteration";
    public static final String ANALYSE_FIELD_ASSIGNMENTS = "analyseFieldAssignments";
    public static final String ANALYSE_CONTEXT = "analyseContext";
    public static final String ANALYSE_CONTAINER = "analyseContainer";
    public static final String ANALYSE_INDEPENDENT_NO_ASSIGNMENT = "analyseIndependentNoAssignment";

    private Map<FieldInfo, FieldAnalyser> fieldAnalysers;

    public ComputedParameterAnalyser(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
        super(analyserContext, parameterInfo);
    }

    @Override
    public void initialize(Stream<FieldAnalyser> fieldAnalyserStream) {
        this.fieldAnalysers = fieldAnalyserStream.collect(Collectors.toUnmodifiableMap(fa -> fa.fieldInfo, fa -> fa));
    }

    record SharedState(int iteration) {
    }

    public final AnalyserComponents<String, SharedState> analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
            .add(CHECK_UNUSED_PARAMETER, this::checkUnusedParameter)
            .add(ANALYSE_FIRST_ITERATION, this::analyseFirstIteration)
            .add(ANALYSE_FIELD_ASSIGNMENTS, this::analyseFieldAssignments)
            .add(ANALYSE_CONTEXT, this::analyseContext)
            .add(ANALYSE_CONTAINER, this::analyseContainer)
            .add(ANALYSE_INDEPENDENT_NO_ASSIGNMENT, this::analyseIndependentNoAssignment)
            .build();

    private AnalysisStatus analyseFirstIteration(SharedState sharedState) {
        assert sharedState.iteration == 0;

        if (parameterInfo.parameterizedType.isPrimitiveExcludingVoid() &&
                !parameterAnalysis.properties.isDone(Property.MODIFIED_OUTSIDE_METHOD)) {
            parameterAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, Level.FALSE_DV);
        }

        // NOTE: a shortcut on immutable to set modification to false is not possible because of casts, see Cast_1
        // NOTE: contractImmutable only has this meaning in iteration 0; once the other two components have been
        // computed, the property IMMUTABLE is not "contract" anymore
        DV formallyImmutable = analyserContext.defaultImmutable(parameterInfo.parameterizedType, false);
        DV contractBefore = parameterAnalysis.getProperty(IMMUTABLE_BEFORE_CONTRACTED);
        DV contractImmutable = parameterAnalysis.getProperty(IMMUTABLE);
        if (contractImmutable.isDone() && formallyImmutable.isDone()
                && !parameterAnalysis.properties.isDone(IMMUTABLE)) {
            DV combined = combineImmutable(formallyImmutable, contractImmutable, contractBefore.valueIsTrue());
            assert combined.isDone();
            parameterAnalysis.setProperty(IMMUTABLE, combined);
        }

        DV contractIndependent = parameterAnalysis.getProperty(INDEPENDENT);
        if (contractIndependent.isDone() && !parameterAnalysis.properties.isDone(INDEPENDENT)) {
            parameterAnalysis.setProperty(INDEPENDENT, contractIndependent);
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
        if (parameterAnalysis.getPropertyFromMapDelayWhenAbsent(IGNORE_MODIFICATIONS).isDelayed() &&
                parameterInfo.parameterizedType.isAbstractInJavaUtilFunction(analyserContext)) {
            parameterAnalysis.setProperty(IGNORE_MODIFICATIONS, Level.TRUE_DV);
        }

        if (parameterAnalysis.getProperty(MODIFIED_VARIABLE).isDelayed()) {
            DV contractIgnoreMod = parameterAnalysis.getPropertyFromMapDelayWhenAbsent(IGNORE_MODIFICATIONS);
            if (contractIgnoreMod.valueIsTrue()) {
                parameterAnalysis.setProperty(MODIFIED_VARIABLE, Level.FALSE_DV);
            }
        }

        if (isNoFieldsInvolved()) {
            noFieldsInvolved();
        }

        return DONE;
    }

    private AnalysisStatus analyseIndependentNoAssignment(SharedState sharedState) {
        if (parameterAnalysis.properties.isDone(INDEPENDENT)) return DONE;

        if (!parameterAnalysis.isAssignedToFieldDelaysResolved()) {
            // we wait until the other analyser has finished, since we need the properties it computes
            return new CausesOfDelay.SimpleSet(parameterInfo, CauseOfDelay.Cause.ASSIGNED_TO_FIELD);
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
            VariableInfo vi = lastStatement.findOrNull(parameterInfo, VariableInfoContainer.Level.MERGE);
            if (vi != null) {
                if (!vi.linkedVariablesIsSet()) {
                    log(org.e2immu.analyser.util.Logger.LogTarget.DELAYED,
                            "Delay independent in parameter {}, waiting for linked1variables in statement {}",
                            parameterInfo.fullyQualifiedName(), lastStatement.index);
                    return new CausesOfDelay.SimpleSet(new CauseOfDelay.VariableCause(parameterInfo, lastStatement.location(), CauseOfDelay.Cause.LINKING));
                }
                List<FieldReference> fields = vi.getLinkedVariables().variables().entrySet().stream()
                        .filter(e -> e.getKey() instanceof FieldReference && e.getValue().ge(LinkedVariables.INDEPENDENT1_DV))
                        .map(e -> (FieldReference) e.getKey()).toList();
                if (!fields.isEmpty()) {
                    // so we know the parameter is content linked to some fields
                    // now the value of independence (from 1 to infinity) is determined by the size of the
                    // hidden content component inside the field

                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(parameterInfo.owner.typeInfo);
                    DV minHiddenContentImmutable = fields.stream()
                            // hidden content is available, because linking has been computed(?)
                            .flatMap(fr -> typeAnalysis.hiddenContentLinkedTo(fr.fieldInfo).stream())
                            .map(pt -> analyserContext.defaultImmutable(pt, false))
                            .reduce(EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV::min);
                    if (minHiddenContentImmutable.isDelayed()) {
                        return minHiddenContentImmutable.causesOfDelay();
                    }
                    int immutableLevel = MultiLevel.level(minHiddenContentImmutable);
                    DV independent = immutableLevel <= MultiLevel.Level.IMMUTABLE_2.level ? INDEPENDENT_1_DV :
                            MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
                    log(ANALYSER, "Assign {} to parameter {}", independent, parameterInfo.fullyQualifiedName());
                    parameterAnalysis.setProperty(INDEPENDENT, independent);
                    return DONE;
                }
            }
        }
        // finally, no other alternative
        parameterAnalysis.setProperty(INDEPENDENT, INDEPENDENT_DV);
        return DONE;
    }

    /*
    Only contracted, but it does inherit.
    Meaning: no argument to this parameter can be of a concrete type that modifies its parameters.
     */
    private AnalysisStatus analyseContainer(SharedState sharedState) {
        DV inMap = parameterAnalysis.getPropertyFromMapDelayWhenAbsent(CONTAINER);
        if (inMap.isDelayed()) {
            DV override = bestOfParameterOverridesForContainer(parameterInfo);
            assert override.isDone();
            parameterAnalysis.setProperty(CONTAINER, override);
        }
        return DONE;
    }

    // can easily be parameterized to other variable properties
    private DV bestOfParameterOverridesForContainer(ParameterInfo parameterInfo) {
        return parameterInfo.owner.methodResolution.get().overrides().stream()
                .filter(mi -> mi.analysisAccessible(InspectionProvider.DEFAULT))
                .map(mi -> {
                    ParameterInfo p = mi.methodInspection.get().getParameters().get(parameterInfo.index);
                    ParameterAnalysis pa = analyserContext.getParameterAnalysis(p);
                    return pa.getPropertyFromMapNeverDelay(Property.CONTAINER);
                }).reduce(Level.FALSE_DV, DV::max);
    }

    /**
     * Copy properties from an effectively final field  (FINAL=Level.TRUE) to the parameter that is is assigned to.
     * Does not apply to variable fields.
     */
    @Override
    public AnalysisStatus analyse(int iteration) {
        try {
            return analyserComponents.run(new SharedState(iteration));
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in parameter analyser, {}", new Location(parameterInfo));
            throw rte;
        }
    }

    private static final Set<Property> PROPERTIES = Set.of(EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD,
            EXTERNAL_IMMUTABLE);

    private static Set<Property> propertiesToCopy(DV assignedOrLinked) {
        if (LinkedVariables.isAssigned(assignedOrLinked)) return PROPERTIES;
        if (assignedOrLinked.equals(LinkedVariables.DEPENDENT_DV)) return Set.of(MODIFIED_OUTSIDE_METHOD);
        return Set.of();
    }

    private AnalysisStatus analyseFieldAssignments(SharedState sharedState) {
        boolean changed = false;
        CausesOfDelay delays = CausesOfDelay.EMPTY;

        // no point, we need to have seen the statement+field analysers first.
        if (sharedState.iteration == 0) {
            return new CausesOfDelay.SimpleSet(parameterInfo, CauseOfDelay.Cause.ASSIGNED_TO_FIELD);
        }

        StatementAnalysis lastStatementAnalysis = analyserContext.getMethodAnalysis(parameterInfo.owner)
                .getLastStatement();
        Set<FieldInfo> fieldsAssignedInThisMethod =
                Resolver.accessibleFieldsStream(analyserContext, parameterInfo.owner.typeInfo,
                                parameterInfo.owner.typeInfo.primaryType())
                        .filter(fieldInfo -> isAssignedIn(lastStatementAnalysis, fieldInfo))
                        .collect(Collectors.toSet());

        // find a field that's linked to me; bail out when not all field's values are set.
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
        if (!parameterAnalysis.assignedToFieldIsFrozen()) {
            parameterAnalysis.freezeAssignedToField();
        }

        Map<FieldInfo, DV> map = parameterAnalysis.getAssignedToField();

        Set<Property> propertiesDelayed = new HashSet<>();
        boolean notAssignedToField = true;
        for (Map.Entry<FieldInfo, DV> e : map.entrySet()) {
            FieldInfo fieldInfo = e.getKey();
            DV assignedOrLinked = e.getValue();
            Set<Property> propertiesToCopy = propertiesToCopy(assignedOrLinked);
            if (LinkedVariables.isAssigned(assignedOrLinked)) notAssignedToField = false;
            FieldAnalyser fieldAnalyser = fieldAnalysers.get(fieldInfo);
            if (fieldAnalyser != null) {
                FieldAnalysis fieldAnalysis = fieldAnalyser.fieldAnalysis;

                for (Property property : propertiesToCopy) {
                    DV inField = fieldAnalysis.getProperty(property);
                    if (inField.isDone()) {
                        if (!parameterAnalysis.properties.isDone(property)) {
                            log(ANALYSER, "Copying value {} from field {} to parameter {} for property {}", inField,
                                    fieldInfo.fullyQualifiedName(), parameterInfo.fullyQualifiedName(), property);
                            parameterAnalysis.setProperty(property, inField);
                            changed = true;
                        }
                    } else {
                        propertiesDelayed.add(property);
                        log(org.e2immu.analyser.util.Logger.LogTarget.DELAYED,
                                "Still delaying copiedFromFieldToParameters because of {}, field {} ~ param {}",
                                property, fieldInfo.name, parameterInfo.name);
                        delays = delays.merge(inField.causesOfDelay());
                    }
                }

                if (!parameterAnalysis.properties.isDone(INDEPENDENT) && (LinkedVariables.isNotIndependent(assignedOrLinked))) {
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
                        log(ANALYSER, "Set @Dependent on parameter {}: linked/assigned to field {}",
                                parameterInfo.fullyQualifiedName(), fieldInfo.name);
                        changed = true;
                    }
                }
            }
        }


        for (Property property : PROPERTIES) {
            if (!parameterAnalysis.properties.isDone(property) && !propertiesDelayed.contains(property)) {
                DV v;
                if (isExternal(property) && notAssignedToField) {
                    v = NOT_INVOLVED_DV;
                } else {
                    v = property.falseDv;
                }
                parameterAnalysis.setProperty(property, v);
                log(ANALYSER, "Wrote false to parameter {} for property {}", parameterInfo.fullyQualifiedName(),
                        property);
                changed = true;
            }
        }

        assert delays.isDelayed() || parameterAnalysis.properties.isDone(Property.MODIFIED_OUTSIDE_METHOD) &&
                parameterAnalysis.properties.isDone(Property.EXTERNAL_NOT_NULL) &&
                parameterAnalysis.properties.isDone(EXTERNAL_IMMUTABLE);

        if (delays.isDelayed()) {
            return delays.addProgress(changed);
        }

        // can be executed multiple times
        parameterAnalysis.resolveFieldDelays();
        return DONE;
    }

    private static boolean isExternal(Property property) {
        return property == EXTERNAL_NOT_NULL || property == EXTERNAL_IMMUTABLE;
    }

    private DV combineImmutable(DV formallyImmutable, DV contractImmutable, boolean contractedBefore) {
        int contractLevel = MultiLevel.level(contractImmutable);
        int formalLevel = MultiLevel.level(formallyImmutable);
        Effective contractEffective = MultiLevel.effective(contractImmutable);
        Effective formalEffective = MultiLevel.effective(formallyImmutable);

        if (contractedBefore) {
            if (contractEffective == EFFECTIVE) {
                if (formalEffective != EVENTUAL || contractLevel != formalLevel) {
                    messages.add(Message.newMessage(parameterAnalysis.location,
                            Message.Label.INCOMPATIBLE_IMMUTABILITY_CONTRACT_BEFORE));
                    return formallyImmutable;
                }
                return MultiLevel.composeImmutable(EVENTUAL_BEFORE, contractLevel);
            }
            messages.add(Message.newMessage(parameterAnalysis.location,
                    Message.Label.INCOMPATIBLE_IMMUTABILITY_CONTRACT_BEFORE_NOT_EVENTUALLY_IMMUTABLE));
            return formallyImmutable;
        }

        if (contractEffective == EVENTUAL_AFTER && formalEffective == EVENTUAL && contractLevel == formalLevel) {
            return contractImmutable;
        }

        if (contractEffective == EFFECTIVE) {
            if (formalEffective != EVENTUAL && formalEffective != EFFECTIVE ||
                    contractLevel != formalLevel) {
                messages.add(Message.newMessage(parameterAnalysis.location,
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
                parameterAnalysis.setProperty(property, NOT_INVOLVED_DV);
            }
        }
        parameterAnalysis.freezeAssignedToField();
        parameterAnalysis.resolveFieldDelays();
    }

    private boolean isAssignedIn(StatementAnalysis lastStatementAnalysis, FieldInfo fieldInfo) {
        VariableInfo vi = lastStatementAnalysis.findOrNull(new FieldReference(analyserContext, fieldInfo),
                VariableInfoContainer.Level.MERGE);
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
                    firstStatement != null && firstStatement.statement instanceof ExplicitConstructorInvocation eci &&
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
            Property.CONTEXT_MODIFIED, CONTEXT_IMMUTABLE};

    private AnalysisStatus analyseContext(SharedState sharedState) {
        // no point, we need to have seen the statement+field analysers first.
        if (sharedState.iteration == 0) {
            return new CausesOfDelay.SimpleSet(parameterInfo, CauseOfDelay.Cause.FIRST_ITERATION);
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
                    log(ANALYSER, "Set {} on parameter {} to {}", property,
                            parameterInfo.fullyQualifiedName(), value);
                    changed = true;
                } else {
                    log(org.e2immu.analyser.util.Logger.LogTarget.DELAYED,
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
            return new CausesOfDelay.SimpleSet(parameterInfo, CauseOfDelay.Cause.FIRST_ITERATION);
        }

        StatementAnalysis lastStatementAnalysis = analyserContext.getMethodAnalysis(parameterInfo.owner)
                .getLastStatement();
        VariableInfo vi = lastStatementAnalysis == null ? null :
                lastStatementAnalysis.findOrNull(parameterInfo, VariableInfoContainer.Level.MERGE);
        if (vi == null || !vi.isRead()) {
            // unused variable
            if (!parameterAnalysis.properties.isDone(Property.MODIFIED_OUTSIDE_METHOD)) {
                parameterAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, Level.FALSE_DV);
            }
            parameterAnalysis.setProperty(Property.CONTEXT_MODIFIED, Level.FALSE_DV);

            // @NotNull
            if (!parameterAnalysis.properties.isDone(Property.EXTERNAL_NOT_NULL)) {
                parameterAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, NOT_INVOLVED_DV);
            }
            parameterAnalysis.setProperty(Property.CONTEXT_NOT_NULL, NULLABLE_DV);

            // @Container: handled separately

            // @IgnoreModifications
            parameterAnalysis.setProperty(IGNORE_MODIFICATIONS, Level.FALSE_DV);

            // @Independent
            if (!parameterAnalysis.properties.isDone(INDEPENDENT)) {
                parameterAnalysis.setProperty(INDEPENDENT, NOT_INVOLVED_DV);
            }

            if (!parameterAnalysis.properties.isDone(EXTERNAL_IMMUTABLE)) {
                parameterAnalysis.setProperty(EXTERNAL_IMMUTABLE, NOT_INVOLVED_DV);
            }
            parameterAnalysis.setProperty(CONTEXT_IMMUTABLE, MUTABLE_DV);

            if (lastStatementAnalysis != null && parameterInfo.owner.isNotOverridingAnyOtherMethod()
                    && !parameterInfo.owner.isCompanionMethod()) {
                messages.add(Message.newMessage(new Location(parameterInfo.owner),
                        Message.Label.UNUSED_PARAMETER, parameterInfo.simpleName()));
            }

            parameterAnalysis.resolveFieldDelays();
            return DONE_ALL; // no point visiting any of the other analysers
        }
        return DONE;
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        return null;
    }
}
