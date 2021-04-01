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
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.resolver.Resolver;
import org.e2immu.annotation.*;
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

public class ParameterAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterAnalyser.class);

    private final Messages messages = new Messages();
    public final ParameterInfo parameterInfo;
    public final ParameterAnalysisImpl.Builder parameterAnalysis;
    private final TypeAnalysis typeAnalysis;

    private Map<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final E2ImmuAnnotationExpressions e2;
    private final AnalyserContext analyserContext;

    public ParameterAnalyser(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
        this.e2 = analyserContext.getE2ImmuAnnotationExpressions();
        this.parameterInfo = parameterInfo;
        parameterAnalysis = new ParameterAnalysisImpl.Builder(analyserContext.getPrimitives(), analyserContext, parameterInfo);
        this.analyserContext = analyserContext;
        this.typeAnalysis = analyserContext.getTypeAnalysis(parameterInfo.owner.typeInfo);
    }

    public ParameterAnalysis getParameterAnalysis() {
        return parameterAnalysis;
    }

    public void initialize(Stream<FieldAnalyser> fieldAnalyserStream) {
        this.fieldAnalysers = fieldAnalyserStream.collect(Collectors.toUnmodifiableMap(fa -> fa.fieldInfo, fa -> fa));
    }

    public void check() {
        log(ANALYSER, "Checking parameter {}", parameterInfo.fullyQualifiedName());

        check(NotModified.class, e2.notModified);
        check(NotNull.class, e2.notNull);
        check(NotNull1.class, e2.notNull1);
        check(NotNull2.class, e2.notNull2);
        check(NotModified1.class, e2.notModified1);
        check(PropagateModification.class, e2.propagateModification);
        check(Dependent1.class, e2.dependent1);
        check(Dependent2.class, e2.dependent2);

        check(BeforeMark.class, e2.beforeMark);
        check(E1Immutable.class, e2.e1Immutable);
        check(E1Container.class, e2.e1Container);
        check(E2Immutable.class, e2.e2Immutable);
        check(E2Container.class, e2.e2Container);

        // opposites
        check(Nullable.class, e2.nullable);
        check(Modified.class, e2.modified);

        checkWorseThanParent();
    }

    public void write() {
        parameterAnalysis.transferPropertiesToAnnotations(analyserContext, e2);
    }

    private static final Set<VariableProperty> CHECK_WORSE_THAN_PARENT = Set.of(NOT_NULL_PARAMETER, MODIFIED_VARIABLE,
            NOT_MODIFIED_1, PROPAGATE_MODIFICATION, IMMUTABLE);

    private void checkWorseThanParent() {
        for (VariableProperty variableProperty : CHECK_WORSE_THAN_PARENT) {
            int valueFromOverrides = analyserContext.getMethodAnalysis(parameterInfo.owner).getOverrides(analyserContext)
                    .stream()
                    .map(ma -> ma.getMethodInfo().methodInspection.get().getParameters().get(parameterInfo.index))
                    .mapToInt(pi -> analyserContext.getParameterAnalysis(pi).getParameterProperty(analyserContext,
                            parameterInfo, variableProperty))
                    .max().orElse(Level.DELAY);
            int value = parameterAnalysis.getProperty(variableProperty);
            if (valueFromOverrides != Level.DELAY && value != Level.DELAY) {
                boolean complain = variableProperty == VariableProperty.MODIFIED_VARIABLE
                        ? value > valueFromOverrides : value < valueFromOverrides;
                if (complain) {
                    messages.add(Message.newMessage(parameterAnalysis.location, Message.WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER,
                            variableProperty.name + ", parameter " + parameterInfo.name));
                }
            }
        }
    }

    private void check(Class<?> annotation, AnnotationExpression annotationExpression) {
        parameterInfo.error(parameterAnalysis, annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(parameterInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    record SharedState(int iteration) {
    }

    public final AnalyserComponents<String, SharedState> analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
            .add("checkUnusedParameter", this::checkUnusedParameter)
            .add("analyseFields", this::analyseFields)
            .add("analyseContext", this::analyseContext)
            .build();

    /**
     * Copy properties from an effectively final field  (FINAL=Level.TRUE) to the parameter that is is assigned to.
     * Does not apply to variable fields.
     */
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

        TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
        int formallyImmutable;
        if (bestType != null) {
            formallyImmutable = analyserContext.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
            if (formallyImmutable == EFFECTIVELY_E2IMMUTABLE) {
                if (!parameterAnalysis.properties.isSet(VariableProperty.MODIFIED_OUTSIDE_METHOD)) {
                    parameterAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
                    changed = true;
                }
                checkLinks = false;
            }
        } else {
            Set<ParameterizedType> implicitlyImmutableDataTypes = typeAnalysis.getImplicitlyImmutableDataTypes();
            if (implicitlyImmutableDataTypes != null &&
                    implicitlyImmutableDataTypes.contains(parameterInfo.parameterizedType)) {
                if (!parameterAnalysis.properties.isSet(VariableProperty.MODIFIED_OUTSIDE_METHOD)) {
                    parameterAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
                    changed = true;
                }
                checkLinks = false;
            }
            formallyImmutable = MultiLevel.NOT_INVOLVED;
        }

        int contractBefore = parameterAnalysis.getProperty(IMMUTABLE_BEFORE_CONTRACTED);
        int contractImmutable = parameterAnalysis.getProperty(IMMUTABLE);
        if (contractImmutable != Level.DELAY && !parameterAnalysis.properties.isSet(IMMUTABLE)) {
            if (formallyImmutable == Level.DELAY) {
                delays = true;
            } else {
                int combined = combineImmutable(formallyImmutable, contractImmutable, contractBefore == Level.TRUE);
                parameterAnalysis.properties.put(IMMUTABLE, combined);
                changed = true;
            }
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
                        log(ANALYSER, "Still delaying copiedFromFieldToParameters because of {}", variableProperty);
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
                                && fr.scope == parameterInfo).findFirst();
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
                if (variableProperty == VariableProperty.EXTERNAL_NOT_NULL && notAssignedToField) {
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

    private int combineImmutable(int formallyImmutable, int contractImmutable, boolean contractedBefore) {
        if (contractedBefore) {
            if (contractImmutable == EFFECTIVELY_E2IMMUTABLE) {
                if (formallyImmutable != EVENTUALLY_E2IMMUTABLE) {
                    messages.add(Message.newMessage(parameterAnalysis.location, Message.INCOMPATIBLE_IMMUTABILITY_CONTRACT,
                            "Contracted to be @E2Immutable @BeforeMark, formal type is not eventually @E2Immutable"));
                    return formallyImmutable;
                }
                return MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK;
            }
            if (contractImmutable == EFFECTIVELY_E1IMMUTABLE) {
                if (formallyImmutable != MultiLevel.EVENTUALLY_E1IMMUTABLE) {
                    messages.add(Message.newMessage(parameterAnalysis.location, Message.INCOMPATIBLE_IMMUTABILITY_CONTRACT,
                            "Contracted to be @E1Immutable @BeforeMark, formal type is not eventually @E1Immutable"));
                    return formallyImmutable;
                }
                return MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK;
            }
            messages.add(Message.newMessage(parameterAnalysis.location, Message.INCOMPATIBLE_IMMUTABILITY_CONTRACT,
                    "Contracted to be @BeforeMark, formal type is not eventually immutable"));
            return formallyImmutable;
        }
        if (contractImmutable == EFFECTIVELY_E2IMMUTABLE) {
            if (formallyImmutable != EVENTUALLY_E2IMMUTABLE && formallyImmutable != EFFECTIVELY_E2IMMUTABLE) {
                messages.add(Message.newMessage(parameterAnalysis.location, Message.INCOMPATIBLE_IMMUTABILITY_CONTRACT,
                        "Contracted to be @E2Immutable after the mark, formal type is not (eventually) @E2Immutable"));
                return formallyImmutable;
            }
            return formallyImmutable == EVENTUALLY_E2IMMUTABLE ? EVENTUALLY_E2IMMUTABLE_AFTER_MARK : EFFECTIVELY_E2IMMUTABLE;
        }
        if (contractImmutable == EFFECTIVELY_E1IMMUTABLE) {
            if (formallyImmutable != EVENTUALLY_E1IMMUTABLE && formallyImmutable != EFFECTIVELY_E1IMMUTABLE) {
                messages.add(Message.newMessage(parameterAnalysis.location, Message.INCOMPATIBLE_IMMUTABILITY_CONTRACT,
                        "Contracted to be @E2Immutable after the mark, formal type is not (eventually) @E1Immutable"));
                return formallyImmutable;
            }
            return formallyImmutable == EVENTUALLY_E1IMMUTABLE ? EVENTUALLY_E1IMMUTABLE_AFTER_MARK : EFFECTIVELY_E1IMMUTABLE;
        }
        if(contractImmutable == MUTABLE) {
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
        This thisVar = new This(analyserContext, fieldInfo.owner);
        VariableInfo vi = lastStatementAnalysis.findOrNull(new FieldReference(analyserContext, fieldInfo, thisVar),
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
                if (param instanceof VariableExpression ve2 && ve2.variable() == parameterInfo) {
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
        VariableInfo vi = methodAnalysis.getLastStatement().getLatestVariableInfo(parameterInfo.fullyQualifiedName());
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
                    log(ANALYSER, "Delays on {} not yet resolved for parameter {}, delaying", variableProperty,
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
            parameterAnalysis.setProperty(CONTEXT_IMMUTABLE, NOT_INVOLVED);

            if (lastStatementAnalysis != null && parameterInfo.owner.isNotOverridingAnyOtherMethod()
                    && !parameterInfo.owner.isCompanionMethod()) {
                messages.add(Message.newMessage(new Location(parameterInfo.owner),
                        Message.UNUSED_PARAMETER, parameterInfo.simpleName()));
            }

            parameterAnalysis.resolveFieldDelays();
            return DONE_ALL; // no point visiting any of the other analysers
        }
        return DONE;
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
