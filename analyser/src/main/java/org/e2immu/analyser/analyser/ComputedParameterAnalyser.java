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
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.resolver.Resolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.model.MultiLevel.*;
import static org.e2immu.analyser.model.ParameterAnalysis.AssignedOrLinked.*;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public class ComputedParameterAnalyser extends ParameterAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputedParameterAnalyser.class);
    public static final String CHECK_UNUSED_PARAMETER = "checkUnusedParameter";
    public static final String ANALYSE_FIELDS = "analyseFields";
    public static final String ANALYSE_CONTEXT = "analyseContext";

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
            .add(ANALYSE_FIELDS, this::analyseFields)
            .add(ANALYSE_CONTEXT, this::analyseContext)
            .build();

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

    private AnalysisStatus analyseFields(SharedState sharedState) {
        boolean changed = false;
        boolean delays = false;
        boolean checkLinks = true;

        if (sharedState.iteration == 0) {
            if (Primitives.isPrimitiveExcludingVoid(parameterInfo.parameterizedType) &&
                    !parameterAnalysis.properties.isSet(VariableProperty.MODIFIED_OUTSIDE_METHOD)) {
                parameterAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
                changed = true;
            }

            // NOTE: a shortcut on immutable to set modification to false is not possible because of casts, see Cast_1
            // NOTE: contractImmutable only has this meaning in iteration 0; once the other two components have been
            // computed, the property IMMUTABLE is not "contract" anymore
            int formallyImmutable = parameterInfo.parameterizedType.defaultImmutable(analyserContext);
            int contractBefore = parameterAnalysis.getProperty(IMMUTABLE_BEFORE_CONTRACTED);
            int contractImmutable = parameterAnalysis.getProperty(IMMUTABLE);
            if (contractImmutable != Level.DELAY && formallyImmutable != Level.DELAY
                    && !parameterAnalysis.properties.isSet(IMMUTABLE)) {
                int combined = combineImmutable(formallyImmutable, contractImmutable, contractBefore == Level.TRUE);
                parameterAnalysis.properties.put(IMMUTABLE, combined);
                changed = true;
            }

            int contractDependent = parameterAnalysis.getProperty(INDEPENDENT_PARAMETER);
            if (contractDependent != Level.DELAY && !parameterAnalysis.properties.isSet(INDEPENDENT_PARAMETER)) {
                parameterAnalysis.properties.put(INDEPENDENT_PARAMETER, contractDependent);
                changed = true;
            }

            int contractPropMod = parameterAnalysis.getProperty(PROPAGATE_MODIFICATION);
            if (contractPropMod != Level.DELAY && !parameterAnalysis.properties.isSet(EXTERNAL_PROPAGATE_MOD)) {
                parameterAnalysis.properties.put(EXTERNAL_PROPAGATE_MOD, contractPropMod);
                changed = true;
            }

            int contractModified = parameterAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE);
            if (contractModified != Level.DELAY && !parameterAnalysis.properties.isSet(VariableProperty.MODIFIED_OUTSIDE_METHOD)) {
                parameterAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, contractModified);
                changed = true;
            }

            if (Primitives.isPrimitiveExcludingVoid(parameterInfo.parameterizedType)) {
                if (!parameterAnalysis.properties.isSet(VariableProperty.EXTERNAL_NOT_NULL)) {
                    parameterAnalysis.setProperty(VariableProperty.EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED); // not involved
                    changed = true;
                }
                checkLinks = false;
            } else {
                int contractNotNull = parameterAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETER);
                if (contractNotNull != Level.DELAY && !parameterAnalysis.properties.isSet(VariableProperty.EXTERNAL_NOT_NULL)) {
                    parameterAnalysis.setProperty(VariableProperty.EXTERNAL_NOT_NULL, contractNotNull);
                    changed = true;
                }
            }
        }

        if (noAssignableFieldsForMethod()) {
            noFieldsInvolvedSetToMultiLevelDELAY();
            return DONE;
        }

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
            ParameterAnalysis.AssignedOrLinked assignedOrLinked = determineAssignedOrLinked(fieldAnalysis, checkLinks);
            if (assignedOrLinked == DELAYED) {
                delays = true;
            } else if (parameterAnalysis.addAssignedToField(fieldInfo, assignedOrLinked)) {
                changed |= assignedOrLinked.isAssignedOrLinked();
            }
        }

        if (delays) {
            return changed ? PROGRESS : DELAYS;
        }
        if (!parameterAnalysis.assignedToFieldIsFrozen()) {
            parameterAnalysis.freezeAssignedToField();
        }

        Map<FieldInfo, ParameterAnalysis.AssignedOrLinked> map = parameterAnalysis.getAssignedToField();

        Set<VariableProperty> propertiesDelayed = new HashSet<>();
        boolean notAssignedToField = true;
        for (Map.Entry<FieldInfo, ParameterAnalysis.AssignedOrLinked> e : map.entrySet()) {
            FieldInfo fieldInfo = e.getKey();
            Set<VariableProperty> propertiesToCopy = e.getValue().propertiesToCopy();
            if (e.getValue() == ASSIGNED) notAssignedToField = false;
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

                // @Dependent1, @Dependent2

                if (!fieldAnalyser.fieldAnalysis.linked1Variables.isSet()) {
                    log(ANALYSER, "Delaying @Dependent1/2, waiting for linked1variables",
                            parameterInfo.owner.typeInfo.fullyQualifiedName);
                    delays = true;
                } else {
                    LinkedVariables lv1 = fieldAnalyser.fieldAnalysis.linked1Variables.get();
                    if (lv1.contains(parameterInfo)) {
                        if (!parameterAnalysis.properties.isSet(VariableProperty.INDEPENDENT)) {
                            parameterAnalysis.properties.put(VariableProperty.INDEPENDENT, MultiLevel.DEPENDENT_1);
                            log(ANALYSER, "Set @Dependent1 on parameter {}: field {} linked or assigned; type is ImplicitlyImmutable",
                                    parameterInfo.fullyQualifiedName(), fieldInfo.name);
                        }
                    } else {
                        Optional<Variable> ov = lv1.variables().stream().filter(v -> v instanceof FieldReference fr
                                && fr.scope instanceof VariableExpression ve && ve.variable() == parameterInfo).findFirst();
                        ov.ifPresent(v -> {
                            if (!parameterAnalysis.properties.isSet(VariableProperty.INDEPENDENT)) {
                                parameterAnalysis.properties.put(VariableProperty.INDEPENDENT, MultiLevel.DEPENDENT_2);
                                log(ANALYSER, "Set @Dependent2 on parameter {}: a field of {} linked or assigned; type is ImplicitlyImmutable",
                                        parameterInfo.fullyQualifiedName(), fieldInfo.name);
                            }
                        });
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

        if (!parameterAnalysis.properties.isSet(VariableProperty.INDEPENDENT) && !delays) {
            parameterAnalysis.properties.put(VariableProperty.INDEPENDENT, MultiLevel.NOT_INVOLVED);
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
        if (contractedBefore) {
            if (contractImmutable == EFFECTIVELY_E2IMMUTABLE) {
                if (formallyImmutable != EVENTUALLY_E2IMMUTABLE) {
                    messages.add(Message.newMessage(parameterAnalysis.location,
                            Message.Label.INCOMPATIBLE_IMMUTABILITY_CONTRACT_BEFORE_NOT_EE2));
                    return formallyImmutable;
                }
                return MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK;
            }
            if (contractImmutable == EFFECTIVELY_E1IMMUTABLE) {
                if (formallyImmutable != MultiLevel.EVENTUALLY_E1IMMUTABLE) {
                    messages.add(Message.newMessage(parameterAnalysis.location,
                            Message.Label.INCOMPATIBLE_IMMUTABILITY_CONTRACT_BEFORE_NOT_EE1));
                    return formallyImmutable;
                }
                return MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK;
            }
            messages.add(Message.newMessage(parameterAnalysis.location,
                    Message.Label.INCOMPATIBLE_IMMUTABILITY_CONTRACT_BEFORE_NOT_EVENTUALLY_IMMUTABLE));
            return formallyImmutable;
        }

        if (contractImmutable == EVENTUALLY_E2IMMUTABLE_AFTER_MARK && formallyImmutable == EVENTUALLY_E2IMMUTABLE) {
            return contractImmutable;
        }

        if (contractImmutable == EVENTUALLY_E1IMMUTABLE_AFTER_MARK && formallyImmutable == EVENTUALLY_E1IMMUTABLE) {
            return contractImmutable;
        }

        if (contractImmutable == EFFECTIVELY_E2IMMUTABLE) {
            if (formallyImmutable != EVENTUALLY_E2IMMUTABLE && formallyImmutable != EFFECTIVELY_E2IMMUTABLE) {
                messages.add(Message.newMessage(parameterAnalysis.location,
                        Message.Label.INCOMPATIBLE_IMMUTABILITY_CONTRACT_AFTER_NOT_EE2));
                return formallyImmutable;
            }
            return formallyImmutable == EVENTUALLY_E2IMMUTABLE ? EVENTUALLY_E2IMMUTABLE_AFTER_MARK : EFFECTIVELY_E2IMMUTABLE;
        }
        if (contractImmutable == EFFECTIVELY_E1IMMUTABLE) {
            if (formallyImmutable != EVENTUALLY_E1IMMUTABLE && formallyImmutable != EFFECTIVELY_E1IMMUTABLE) {
                messages.add(Message.newMessage(parameterAnalysis.location,
                        Message.Label.INCOMPATIBLE_IMMUTABILITY_CONTRACT_AFTER_NOT_EE1));
                return formallyImmutable;
            }
            return formallyImmutable == EVENTUALLY_E1IMMUTABLE ? EVENTUALLY_E1IMMUTABLE_AFTER_MARK : EFFECTIVELY_E1IMMUTABLE;
        }
        if (contractImmutable == MUTABLE) {
            return formallyImmutable;
        }
        throw new UnsupportedOperationException("Should have covered all the bases");
    }

    private boolean noAssignableFieldsForMethod() {
        boolean methodIsStatic = parameterInfo.owner.methodInspection.get().isStatic();
        return parameterInfo.owner.typeInfo.typeInspection.get().fields().stream()
                .filter(fieldInfo -> !methodIsStatic || fieldInfo.isStatic())
                .allMatch(fieldInfo -> fieldInfo.isExplicitlyFinal() && !parameterInfo.owner.isConstructor);
    }

    private void noFieldsInvolvedSetToMultiLevelDELAY() {
        for (VariableProperty variableProperty : PROPERTIES) {
            if (!parameterAnalysis.properties.isSet(variableProperty)) {
                parameterAnalysis.setProperty(variableProperty, MultiLevel.NOT_INVOLVED);
            }
        }
        if (!parameterAnalysis.properties.isSet(VariableProperty.INDEPENDENT)) {
            parameterAnalysis.properties.put(VariableProperty.INDEPENDENT, MultiLevel.NOT_INVOLVED);
        }
        parameterAnalysis.freezeAssignedToField();
        parameterAnalysis.resolveFieldDelays();
    }

    private boolean isAssignedIn(StatementAnalysis lastStatementAnalysis, FieldInfo fieldInfo) {
        VariableInfo vi = lastStatementAnalysis.findOrNull(new FieldReference(analyserContext, fieldInfo),
                VariableInfoContainer.Level.MERGE);
        return vi != null && vi.isAssigned();
    }

    private ParameterAnalysis.AssignedOrLinked determineAssignedOrLinked(FieldAnalysis fieldAnalysis, boolean checkLinks) {
        int effFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effFinal == Level.DELAY) {
            return DELAYED;
        }
        if (effFinal == Level.TRUE) {
            Expression effectivelyFinal = fieldAnalysis.getEffectivelyFinalValue();
            if (effectivelyFinal == null) {
                return DELAYED;
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
        if (!checkLinks) return NO;

        // variable field, no direct assignment to parameter
        LinkedVariables linked = fieldAnalysis.getLinkedVariables();
        if (linked == LinkedVariables.DELAY) {
            return DELAYED;
        }
        return linked.variables().contains(parameterInfo) ? ParameterAnalysis.AssignedOrLinked.LINKED : NO;
    }

    public static final VariableProperty[] CONTEXT_PROPERTIES = {VariableProperty.CONTEXT_NOT_NULL,
            VariableProperty.CONTEXT_MODIFIED, CONTEXT_PROPAGATE_MOD, CONTEXT_DEPENDENT, CONTEXT_IMMUTABLE};

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

            // @NotNull1
            parameterAnalysis.setProperty(VariableProperty.NOT_MODIFIED_1, Level.FALSE);

            // @PropagateModification
            if (!parameterAnalysis.properties.isSet(EXTERNAL_PROPAGATE_MOD)) {
                parameterAnalysis.setProperty(EXTERNAL_PROPAGATE_MOD, Level.FALSE);
            }
            parameterAnalysis.setProperty(CONTEXT_PROPAGATE_MOD, Level.FALSE);

            // @Independent
            if (!parameterAnalysis.properties.isSet(VariableProperty.INDEPENDENT)) {
                parameterAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.NOT_INVOLVED);
            }
            parameterAnalysis.setProperty(CONTEXT_DEPENDENT, parameterInfo.parameterizedType.defaultIndependent());

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