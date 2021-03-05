/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MultiValue;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.analyser.VariableProperty.MODIFIED_OUTSIDE_METHOD;
import static org.e2immu.analyser.analyser.VariableProperty.NOT_NULL_PARAMETER;
import static org.e2immu.analyser.model.ParameterAnalysis.AssignedOrLinked.*;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public class ParameterAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterAnalyser.class);

    private final Messages messages = new Messages();
    public final ParameterInfo parameterInfo;
    public final ParameterAnalysisImpl.Builder parameterAnalysis;

    private Map<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final E2ImmuAnnotationExpressions e2;
    private final AnalyserContext analyserContext;

    public ParameterAnalyser(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
        this.e2 = analyserContext.getE2ImmuAnnotationExpressions();
        this.parameterInfo = parameterInfo;
        parameterAnalysis = new ParameterAnalysisImpl.Builder(analyserContext.getPrimitives(), analyserContext, parameterInfo);
        this.analyserContext = analyserContext;
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

        // opposites
        check(Nullable.class, e2.nullable);
        check(Modified.class, e2.modified);

        checkWorseThanParent();
    }

    public void write() {
        parameterAnalysis.transferPropertiesToAnnotations(analyserContext, e2);
    }

    private static final Set<VariableProperty> CHECK_WORSE_THAN_PARENT = Set.of(NOT_NULL_PARAMETER, MODIFIED_OUTSIDE_METHOD);

    private void checkWorseThanParent() {
        for (VariableProperty variableProperty : CHECK_WORSE_THAN_PARENT) {
            int valueFromOverrides = analyserContext.getMethodAnalysis(parameterInfo.owner).getOverrides(analyserContext)
                    .stream()
                    .map(ma -> ma.getMethodInfo().methodInspection.get().getParameters().get(parameterInfo.index))
                    .mapToInt(pi -> analyserContext.getParameterAnalysis(pi).getProperty(variableProperty))
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
        if (bestType != null) {
            int immutable = analyserContext.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
            if (immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                if (!parameterAnalysis.properties.isSet(VariableProperty.MODIFIED_OUTSIDE_METHOD)) {
                    parameterAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
                    changed = true;
                }
                checkLinks = false;
            }
        } else if(parameterInfo.parameterizedType.isUnboundParameterType()) {
            if (!parameterAnalysis.properties.isSet(VariableProperty.MODIFIED_OUTSIDE_METHOD)) {
                parameterAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
                changed = true;
            }
            checkLinks = false;
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
        This thisVar = new This(analyserContext, parameterInfo.owner.typeInfo);
        Set<FieldInfo> fieldsAssignedInThisMethod = parameterInfo.owner.typeInfo.typeInspection.get().fields().stream()
                .filter(fieldInfo -> isAssignedIn(lastStatementAnalysis, thisVar, fieldInfo)).collect(Collectors.toSet());

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
            } else {
                assert e.getValue() == NO;
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
        assert delays || parameterAnalysis.properties.isSet(VariableProperty.MODIFIED_OUTSIDE_METHOD) &&
                parameterAnalysis.properties.isSet(VariableProperty.EXTERNAL_NOT_NULL);

        if (delays) {
            return changed ? PROGRESS : DELAYS;
        }

        // can be executed multiple times
        parameterAnalysis.resolveFieldDelays();
        return DONE;
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
        parameterAnalysis.freezeAssignedToField();
        parameterAnalysis.resolveFieldDelays();
    }

    private boolean isAssignedIn(StatementAnalysis lastStatementAnalysis, This thisVar, FieldInfo fieldInfo) {
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
            if ((ve = effectivelyFinal.asInstanceOf(VariableExpression.class)) != null && ve.variable() == parameterInfo) {
                return ASSIGNED;
            }
            if (effectivelyFinal instanceof MultiValue multiValue &&
                    Arrays.stream(multiValue.multiExpression.expressions())
                            .anyMatch(e -> {
                                VariableExpression ve2;
                                return (ve2 = e.asInstanceOf(VariableExpression.class)) != null
                                        && ve2.variable() == parameterInfo;
                            })) {
                return ASSIGNED;
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
            VariableProperty.CONTEXT_MODIFIED};

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
                if (vi.getProperty(variableProperty) != Level.DELAY) {
                    int value = vi.getProperty(variableProperty, variableProperty.falseValue);
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

            int notNull = parameterInfo.parameterizedType.defaultNotNull();
            if (!parameterAnalysis.properties.isSet(VariableProperty.EXTERNAL_NOT_NULL)) {
                parameterAnalysis.setProperty(VariableProperty.EXTERNAL_NOT_NULL, notNull);
            }
            parameterAnalysis.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.NULLABLE);
            parameterAnalysis.setProperty(VariableProperty.NOT_MODIFIED_1, Level.FALSE);

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
