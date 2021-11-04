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

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.analyser.LinkedVariables.ASSIGNED;
import static org.e2immu.analyser.analyser.LinkedVariables.DELAYED_VALUE;
import static org.e2immu.analyser.analyser.VariableProperty.INDEPENDENT;
import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.model.MultiLevel.*;
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

        if (Primitives.isPrimitiveExcludingVoid(parameterInfo.parameterizedType) &&
                !parameterAnalysis.properties.isSet(VariableProperty.MODIFIED_OUTSIDE_METHOD)) {
            parameterAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
        }

        // NOTE: a shortcut on immutable to set modification to false is not possible because of casts, see Cast_1
        // NOTE: contractImmutable only has this meaning in iteration 0; once the other two components have been
        // computed, the property IMMUTABLE is not "contract" anymore
        int formallyImmutable = parameterInfo.parameterizedType.defaultImmutable(analyserContext, false);
        int contractBefore = parameterAnalysis.getProperty(IMMUTABLE_BEFORE_CONTRACTED);
        int contractImmutable = parameterAnalysis.getProperty(IMMUTABLE);
        if (contractImmutable != Level.DELAY && formallyImmutable != Level.DELAY
                && !parameterAnalysis.properties.isSet(IMMUTABLE)) {
            int combined = combineImmutable(formallyImmutable, contractImmutable, contractBefore == Level.TRUE);
            parameterAnalysis.properties.put(IMMUTABLE, combined);
        }

        int contractIndependent = parameterAnalysis.getProperty(INDEPENDENT);
        if (contractIndependent != Level.DELAY && !parameterAnalysis.properties.isSet(INDEPENDENT)) {
            parameterAnalysis.properties.put(INDEPENDENT, contractIndependent);
        }

        int contractModified = parameterAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE);
        if (contractModified != Level.DELAY && !parameterAnalysis.properties.isSet(VariableProperty.MODIFIED_OUTSIDE_METHOD)) {
            parameterAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, contractModified);
        }

        if (Primitives.isPrimitiveExcludingVoid(parameterInfo.parameterizedType)) {
            if (!parameterAnalysis.properties.isSet(VariableProperty.EXTERNAL_NOT_NULL)) {
                parameterAnalysis.setProperty(VariableProperty.EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED); // not involved
            }
        } else {
            int contractNotNull = parameterAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETER);
            if (contractNotNull != Level.DELAY && !parameterAnalysis.properties.isSet(VariableProperty.EXTERNAL_NOT_NULL)) {
                parameterAnalysis.setProperty(VariableProperty.EXTERNAL_NOT_NULL, contractNotNull);
            }
        }

        // implicit @IgnoreModifications rule for java.util.function
        if (parameterAnalysis.getPropertyFromMapDelayWhenAbsent(IGNORE_MODIFICATIONS) == Level.DELAY &&
                parameterInfo.parameterizedType.isAbstractInJavaUtilFunction(analyserContext)) {
            parameterAnalysis.setProperty(IGNORE_MODIFICATIONS, Level.TRUE);
        }

        if (parameterAnalysis.getProperty(MODIFIED_VARIABLE) == Level.DELAY) {
            int contractIgnoreMod = parameterAnalysis.getPropertyFromMapDelayWhenAbsent(IGNORE_MODIFICATIONS);
            if (contractIgnoreMod == Level.TRUE) {
                parameterAnalysis.setProperty(MODIFIED_VARIABLE, Level.FALSE);
            }
        }

        if (isNoFieldsInvolved()) {
            noFieldsInvolved();
        }

        return DONE;
    }

    private AnalysisStatus analyseIndependentNoAssignment(SharedState sharedState) {
        if (parameterAnalysis.properties.isSet(INDEPENDENT)) return DONE;

        if (!parameterAnalysis.isAssignedToFieldDelaysResolved()) {
            // we wait until the other analyser has finished, since we need the properties it computes
            return DELAYS;
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
                    return DELAYS;
                }
                List<FieldReference> fields = vi.getLinkedVariables().variables().entrySet().stream()
                        .filter(e -> e.getKey() instanceof FieldReference && e.getValue() >= LinkedVariables.INDEPENDENT1)
                        .map(e -> (FieldReference) e.getKey()).toList();
                if (!fields.isEmpty()) {
                    // so we know the parameter is content linked to some fields
                    // now the value of independence (from 1 to infinity) is determined by the size of the
                    // hidden content component inside the field

                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(parameterInfo.owner.typeInfo);
                    int minHiddenContentImmutable = fields.stream()
                            .flatMap(fr -> typeAnalysis.hiddenContentLinkedTo(fr.fieldInfo).stream())
                            .mapToInt(pt -> pt.defaultImmutable(analyserContext, false))
                            .min().orElse(EFFECTIVELY_RECURSIVELY_IMMUTABLE);
                    if (minHiddenContentImmutable == Level.DELAY) {
                        log(org.e2immu.analyser.util.Logger.LogTarget.DELAYED,
                                "Delay independent in parameter {}, waiting for independent of fields {}",
                                parameterInfo.fullyQualifiedName(), fields);
                        return DELAYS;
                    }
                    int immutableLevel = MultiLevel.level(minHiddenContentImmutable);
                    int independent = immutableLevel <= LEVEL_2_IMMUTABLE ? INDEPENDENT_1 :
                            MultiLevel.independentCorrespondingToImmutableLevel(immutableLevel);
                    log(ANALYSER, "Assign {} to parameter {}", MultiLevel.niceIndependent(independent),
                            parameterInfo.fullyQualifiedName());
                    parameterAnalysis.setProperty(INDEPENDENT, independent);
                    return DONE;
                }
            }
        }
        // finally, no other alternative
        parameterAnalysis.setProperty(INDEPENDENT, MultiLevel.INDEPENDENT);
        return DONE;
    }


    private AnalysisStatus analyseContainer(SharedState sharedState) {
        int inMap = parameterAnalysis.getPropertyFromMapDelayWhenAbsent(CONTAINER);
        if (inMap == Level.DELAY) {
            int value;
            TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo(analyserContext);
            if (bestType == null) {
                value = Level.TRUE;
            } else if (Primitives.isPrimitiveExcludingVoid(bestType)) {
                value = Level.TRUE;
            } else {
                int override = bestOfParameterOverridesForContainer(parameterInfo);
                if (override == Level.DELAY) {
                    return DELAYS;
                }
                int typeContainer = analyserContext.getTypeAnalysis(bestType).getProperty(CONTAINER);
                if (typeContainer == Level.DELAY) {
                    return DELAYS;
                }
                value = Math.max(override, typeContainer);
            }
            parameterAnalysis.setProperty(CONTAINER, value);
        }
        return DONE;
    }

    // can easily be parameterized to other variable properties
    private int bestOfParameterOverridesForContainer(ParameterInfo parameterInfo) {
        return parameterInfo.owner.methodResolution.get().overrides().stream()
                .filter(mi -> mi.analysisAccessible(InspectionProvider.DEFAULT))
                .mapToInt(mi -> {
                    ParameterInfo p = mi.methodInspection.get().getParameters().get(parameterInfo.index);
                    ParameterAnalysis pa = analyserContext.getParameterAnalysis(p);
                    return pa.getPropertyFromMapNeverDelay(VariableProperty.CONTAINER);
                }).max().orElse(VariableProperty.CONTAINER.falseValue);
    }


    @Override
    protected String where(String componentName) {
        return parameterInfo.fullyQualifiedName() + ":" + componentName;
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

    private static final Set<VariableProperty> PROPERTIES = Set.of(EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD,
            EXTERNAL_IMMUTABLE);

    private static Set<VariableProperty> propertiesToCopy(int assignedOrLinked) {
        if (LinkedVariables.isAssigned(assignedOrLinked)) return PROPERTIES;
        if (assignedOrLinked == LinkedVariables.DEPENDENT) return Set.of(MODIFIED_OUTSIDE_METHOD);
        return Set.of();
    }

    private AnalysisStatus analyseFieldAssignments(SharedState sharedState) {
        boolean changed = false;
        boolean delays = false;

        // no point, we need to have seen the statement+field analysers first.
        if (sharedState.iteration == 0) return DELAYS;

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
            int assignedOrLinked = determineAssignedOrLinked(fieldAnalysis);
            if (assignedOrLinked == DELAYED_VALUE) {
                delays = true;
            } else if (parameterAnalysis.addAssignedToField(fieldInfo, assignedOrLinked)) {
                changed |= LinkedVariables.isAssignedOrLinked(assignedOrLinked);
            }
        }

        if (delays) {
            return changed ? PROGRESS : DELAYS;
        }
        if (!parameterAnalysis.assignedToFieldIsFrozen()) {
            parameterAnalysis.freezeAssignedToField();
        }

        Map<FieldInfo, Integer> map = parameterAnalysis.getAssignedToField();

        Set<VariableProperty> propertiesDelayed = new HashSet<>();
        boolean notAssignedToField = true;
        for (Map.Entry<FieldInfo, Integer> e : map.entrySet()) {
            FieldInfo fieldInfo = e.getKey();
            int assignedOrLinked = e.getValue();
            Set<VariableProperty> propertiesToCopy = propertiesToCopy(assignedOrLinked);
            if (LinkedVariables.isAssigned(assignedOrLinked)) notAssignedToField = false;
            FieldAnalyser fieldAnalyser = fieldAnalysers.get(fieldInfo);
            if (fieldAnalyser != null) {
                FieldAnalysis fieldAnalysis = fieldAnalyser.fieldAnalysis;

                for (VariableProperty variableProperty : propertiesToCopy) {
                    int inField = fieldAnalysis.getProperty(variableProperty);
                    if (inField != Level.DELAY) {
                        if (!parameterAnalysis.properties.isSet(variableProperty)) {
                            log(ANALYSER, "Copying value {} from field {} to parameter {} for property {}", inField,
                                    fieldInfo.fullyQualifiedName(), parameterInfo.fullyQualifiedName(), variableProperty);
                            parameterAnalysis.setProperty(variableProperty, inField);
                            changed = true;
                        }
                    } else {
                        propertiesDelayed.add(variableProperty);
                        log(org.e2immu.analyser.util.Logger.LogTarget.DELAYED,
                                "Still delaying copiedFromFieldToParameters because of {}, field {} ~ param {}",
                                variableProperty, fieldInfo.name, parameterInfo.name);
                        delays = true;
                    }
                }

                if (!parameterAnalysis.properties.isSet(INDEPENDENT) && (LinkedVariables.isNotIndependent(assignedOrLinked))) {
                    int immutable = parameterInfo.parameterizedType.defaultImmutable(analyserContext, false);
                    if (immutable == Level.DELAY) {
                        delays = true;
                    } else {
                        int levelImmutable = MultiLevel.level(immutable);
                        int typeIndependent;
                        if (levelImmutable <= LEVEL_1_IMMUTABLE) {
                            if (assignedOrLinked <= LinkedVariables.DEPENDENT) {
                                typeIndependent = MultiLevel.DEPENDENT;
                            } else {
                                typeIndependent = INDEPENDENT_1;
                            }
                        } else {
                            typeIndependent = MultiLevel.independentCorrespondingToImmutableLevel(levelImmutable);
                        }
                        parameterAnalysis.properties.put(INDEPENDENT, typeIndependent);
                        log(ANALYSER, "Set @Dependent on parameter {}: linked/assigned to field {}",
                                parameterInfo.fullyQualifiedName(), fieldInfo.name);
                        changed = true;
                    }
                }
            }
        }


        for (VariableProperty variableProperty : PROPERTIES) {
            if (!parameterAnalysis.properties.isSet(variableProperty) && !propertiesDelayed.contains(variableProperty)) {
                int v;
                if (isExternal(variableProperty) && notAssignedToField) {
                    v = MultiLevel.NOT_INVOLVED;
                } else {
                    v = variableProperty.falseValue;
                }
                parameterAnalysis.setProperty(variableProperty, v);
                log(ANALYSER, "Wrote false to parameter {} for property {}", parameterInfo.fullyQualifiedName(),
                        variableProperty);
                changed = true;
            }
        }

        assert delays || parameterAnalysis.properties.isSet(VariableProperty.MODIFIED_OUTSIDE_METHOD) &&
                parameterAnalysis.properties.isSet(VariableProperty.EXTERNAL_NOT_NULL) &&
                parameterAnalysis.properties.isSet(EXTERNAL_IMMUTABLE);

        if (delays) {
            return changed ? PROGRESS : DELAYS;
        }

        // can be executed multiple times
        parameterAnalysis.resolveFieldDelays();
        return DONE;
    }

    private static boolean isExternal(VariableProperty variableProperty) {
        return variableProperty == EXTERNAL_NOT_NULL || variableProperty == EXTERNAL_IMMUTABLE;
    }

    private int combineImmutable(int formallyImmutable, int contractImmutable, boolean contractedBefore) {
        int contractLevel = MultiLevel.level(contractImmutable);
        int formalLevel = MultiLevel.level(formallyImmutable);
        int contractEffective = MultiLevel.effective(contractImmutable);
        int formalEffective = MultiLevel.effective(formallyImmutable);

        if (contractedBefore) {
            if (contractEffective == EFFECTIVE) {
                if (formalEffective != EVENTUAL || contractLevel != formalLevel) {
                    messages.add(Message.newMessage(parameterAnalysis.location,
                            Message.Label.INCOMPATIBLE_IMMUTABILITY_CONTRACT_BEFORE));
                    return formallyImmutable;
                }
                return MultiLevel.compose(EVENTUAL_BEFORE, contractLevel);
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
            return formalEffective == EVENTUAL ? MultiLevel.compose(EVENTUAL_AFTER, contractLevel) : contractImmutable;
        }

        if (contractImmutable == MUTABLE) {
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
        for (VariableProperty variableProperty : PROPERTIES) {
            if (!parameterAnalysis.properties.isSet(variableProperty)) {
                parameterAnalysis.setProperty(variableProperty, MultiLevel.NOT_INVOLVED);
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

    private int determineAssignedOrLinked(FieldAnalysis fieldAnalysis) {
        int effFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effFinal == Level.DELAY) {
            return DELAYED_VALUE;
        }
        if (effFinal == Level.TRUE) {
            Expression effectivelyFinal = fieldAnalysis.getEffectivelyFinalValue();
            if (effectivelyFinal == null) {
                return DELAYED_VALUE;
            }
            VariableExpression ve;

            // == parameterInfo works fine unless a super(...) has been used
            if ((ve = effectivelyFinal.asInstanceOf(VariableExpression.class)) != null && ve.variable() == parameterInfo) {
                return ASSIGNED;
            }
            // the case of multiple constructors
            if (effectivelyFinal instanceof MultiValue multiValue &&
                    Arrays.stream(multiValue.multiExpression.expressions())
                            .anyMatch(e -> {
                                VariableExpression ve2;
                                return (ve2 = e.asInstanceOf(VariableExpression.class)) != null
                                        && ve2.variable() == parameterInfo;
                            })) {
                return ASSIGNED;
            }
            // the case of this(...) or super(...)
            StatementAnalysis firstStatement = analyserContext.getMethodAnalysis(parameterInfo.owner).getFirstStatement();
            if (ve != null && ve.variable() instanceof ParameterInfo pi &&
                    firstStatement != null && firstStatement.statement instanceof ExplicitConstructorInvocation eci &&
                    eci.methodInfo == pi.owner) {
                Expression param = eci.structure.updaters().get(pi.index);
                VariableExpression ve2;
                if ((ve2 = param.asInstanceOf(VariableExpression.class)) != null && ve2.variable() == parameterInfo) {
                    return ASSIGNED;
                }
            }
        }

        // variable field, no direct assignment to parameter
        LinkedVariables linked = fieldAnalysis.getLinkedVariables();
        return linked.variables().getOrDefault(parameterInfo, LinkedVariables.NO_LINKING);
    }

    public static final VariableProperty[] CONTEXT_PROPERTIES = {VariableProperty.CONTEXT_NOT_NULL,
            VariableProperty.CONTEXT_MODIFIED, CONTEXT_IMMUTABLE};

    private AnalysisStatus analyseContext(SharedState sharedState) {
        // no point, we need to have seen the statement+field analysers first.
        if (sharedState.iteration == 0) return DELAYS;

        // context not null, context modified
        MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(parameterInfo.owner);
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        VariableInfo vi = lastStatement.getLatestVariableInfo(parameterInfo.fullyQualifiedName());
        boolean delayFromContext = false;
        boolean changed = false;
        for (VariableProperty variableProperty : CONTEXT_PROPERTIES) {
            if (!parameterAnalysis.properties.isSet(variableProperty)) {
                int value = vi.getProperty(variableProperty);
                if (value != Level.DELAY) {
                    parameterAnalysis.setProperty(variableProperty, value);
                    log(ANALYSER, "Set {} on parameter {} to {}", variableProperty,
                            parameterInfo.fullyQualifiedName(), value);
                    changed = true;
                } else {
                    assert translatedDelay(ANALYSE_CONTEXT,
                            vi.variable().fullyQualifiedName() + "@" + lastStatement.index + "." + variableProperty.name(),
                            parameterInfo.fullyQualifiedName() + "." + variableProperty.name());

                    log(org.e2immu.analyser.util.Logger.LogTarget.DELAYED,
                            "Delays on {} not yet resolved for parameter {}, delaying", variableProperty,
                            parameterInfo.fullyQualifiedName());
                    delayFromContext = true;
                }
            }
        }
        if (delayFromContext) {
            return changed ? PROGRESS : DELAYS;
        }
        return DONE;
    }

    private AnalysisStatus checkUnusedParameter(SharedState sharedState) {
        // no point, we need to have seen the statement+field analysers first.
        if (sharedState.iteration == 0) return DELAYS;

        StatementAnalysis lastStatementAnalysis = analyserContext.getMethodAnalysis(parameterInfo.owner)
                .getLastStatement();
        VariableInfo vi = lastStatementAnalysis == null ? null :
                lastStatementAnalysis.findOrNull(parameterInfo, VariableInfoContainer.Level.MERGE);
        if (vi == null || !vi.isRead()) {
            // unused variable
            if (!parameterAnalysis.properties.isSet(VariableProperty.MODIFIED_OUTSIDE_METHOD)) {
                parameterAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
            }
            parameterAnalysis.setProperty(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);

            // @NotNull
            int notNull = parameterInfo.parameterizedType.defaultNotNull();
            if (!parameterAnalysis.properties.isSet(VariableProperty.EXTERNAL_NOT_NULL)) {
                parameterAnalysis.setProperty(VariableProperty.EXTERNAL_NOT_NULL, notNull);
            }
            parameterAnalysis.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.NULLABLE);

            // @Container: handled separately

            // @IgnoreModifications
            parameterAnalysis.setProperty(IGNORE_MODIFICATIONS, Level.FALSE);

            // @Independent
            if (!parameterAnalysis.properties.isSet(INDEPENDENT)) {
                parameterAnalysis.setProperty(INDEPENDENT, MultiLevel.NOT_INVOLVED);
            }

            if (!parameterAnalysis.properties.isSet(EXTERNAL_IMMUTABLE)) {
                parameterAnalysis.setProperty(EXTERNAL_IMMUTABLE, NOT_INVOLVED);
            }
            parameterAnalysis.setProperty(CONTEXT_IMMUTABLE, MUTABLE);

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
