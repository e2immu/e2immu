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
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.model.ParameterAnalysis.AssignedOrLinked.*;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public class ParameterAnalyser {
    private final Messages messages = new Messages();
    public final ParameterInfo parameterInfo;
    public final ParameterAnalysisImpl.Builder parameterAnalysis;

    private Map<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final E2ImmuAnnotationExpressions e2;
    private final AnalysisProvider analysisProvider;

    public ParameterAnalyser(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
        this.e2 = analyserContext.getE2ImmuAnnotationExpressions();
        this.parameterInfo = parameterInfo;
        parameterAnalysis = new ParameterAnalysisImpl.Builder(analyserContext.getPrimitives(), analyserContext, parameterInfo);
        analysisProvider = analyserContext;
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
        parameterAnalysis.transferPropertiesToAnnotations(analysisProvider, e2);
    }

    private void checkWorseThanParent() {
        for (VariableProperty variableProperty : VariableProperty.CHECK_WORSE_THAN_PARENT) {
            int valueFromOverrides = analysisProvider.getMethodAnalysis(parameterInfo.owner).getOverrides(analysisProvider)
                    .stream()
                    .map(ma -> ma.getMethodInfo().methodInspection.get().getParameters().get(parameterInfo.index))
                    .mapToInt(pi -> analysisProvider.getParameterAnalysis(pi).getProperty(variableProperty))
                    .max().orElse(Level.DELAY);
            int value = parameterAnalysis.getProperty(variableProperty);
            if (valueFromOverrides != Level.DELAY && value != Level.DELAY) {
                boolean complain = variableProperty == VariableProperty.MODIFIED ? value > valueFromOverrides : value < valueFromOverrides;
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

    /**
     * Copy properties from an effectively final field  (FINAL=Level.TRUE) to the parameter that is is assigned to.
     * Does not apply to variable fields.
     */
    public AnalysisStatus analyse() {
        if (checkUnusedParameter()) return DONE;

        boolean changed = false;
        boolean delays = false;
        // find a field that's linked to me; bail out when not all field's values are set.
        for (FieldInfo fieldInfo : parameterInfo.owner.typeInfo.typeInspection.get().fields()) {
            FieldAnalysis fieldAnalysis = analysisProvider.getFieldAnalysis(fieldInfo);
            ParameterAnalysis.AssignedOrLinked assignedOrLinked = determineAssignedOrLinked(fieldAnalysis);
            if (assignedOrLinked == DELAYED) {
                delays = true;
            } else if (parameterAnalysis.addAssignedToField(fieldInfo, assignedOrLinked)) {
                changed |= assignedOrLinked.isAssignedOrLinked();
            }
        }
        if (delays) {
            return changed ? PROGRESS : DELAYS;
        }

        Map<FieldInfo, ParameterAnalysis.AssignedOrLinked> map = parameterAnalysis.getAssignedToField();
        if (checkNotLinkedOrAssigned(map)) return DONE;

        for (Map.Entry<FieldInfo, ParameterAnalysis.AssignedOrLinked> e : map.entrySet()) {
            FieldInfo fieldInfo = e.getKey();
            Set<VariableProperty> propertiesToCopy = e.getValue() == ASSIGNED ? VariableProperty.FROM_FIELD_TO_PARAMETER :
                    Set.of(VariableProperty.MODIFIED);
            FieldAnalysis fieldAnalysis = fieldAnalysers.get(fieldInfo).fieldAnalysis;

            for (VariableProperty variableProperty : propertiesToCopy) {
                int inField = fieldAnalysis.getProperty(variableProperty);
                if (inField != Level.DELAY) {
                    int inParameter = parameterAnalysis.getProperty(variableProperty);
                    if (inField > inParameter) {
                        log(ANALYSER, "Copying value {} from field {} to parameter {} for property {}", inField,
                                fieldInfo.fullyQualifiedName(), parameterInfo.fullyQualifiedName(), variableProperty);
                        parameterAnalysis.setProperty(variableProperty, inField);
                        changed = true;
                    }
                } else {
                    log(ANALYSER, "Still delaying copiedFromFieldToParameters because of {}", variableProperty);
                    delays = true;
                }
            }
        }
        if (!delays) {
            log(ANALYSER, "No delays anymore on copying from field to parameter");
            parameterAnalysis.resolveFieldDelays();
            return DONE;
        }
        return changed ? AnalysisStatus.PROGRESS : AnalysisStatus.DELAYS;
    }

    private ParameterAnalysis.AssignedOrLinked determineAssignedOrLinked(FieldAnalysis fieldAnalysis) {
        int effFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effFinal == Level.DELAY) {
            return DELAYED;
        }
        if (effFinal == Level.TRUE) {
            Expression effectivelyFinal = fieldAnalysis.getEffectivelyFinalValue();
            if (effectivelyFinal == null) {
                return DELAYED;
            }
            VariableExpression variableValue;
            if ((variableValue = effectivelyFinal.asInstanceOf(VariableExpression.class)) != null
                    && variableValue.variable() == parameterInfo) {
                return ASSIGNED;
            }
        }
        // variable field, no direct assignment to parameter
        LinkedVariables linked = fieldAnalysis.getLinkedVariables();
        if (linked == LinkedVariables.DELAY) {
            return DELAYED;
        }
        return linked.variables().contains(parameterInfo) ? LINKED : NO;
    }

    private boolean checkNotLinkedOrAssigned(Map<FieldInfo, ParameterAnalysis.AssignedOrLinked> map) {
        if (map.values().stream().allMatch(v -> v == NO)) {
            StatementAnalysis lastStatementAnalysis = analysisProvider.getMethodAnalysis(parameterInfo.owner).getLastStatement();
            VariableInfo vi = lastStatementAnalysis.findOrNull(parameterInfo, VariableInfoContainer.Level.MERGE);
            int notNullDelayResolved = vi.getProperty(VariableProperty.NOT_NULL_DELAYS_RESOLVED);
            if (notNullDelayResolved != Level.FALSE && parameterAnalysis.getProperty(VariableProperty.NOT_NULL) == Level.DELAY) {
                parameterAnalysis.setProperty(VariableProperty.NOT_NULL, MultiLevel.MUTABLE);
            }
            parameterAnalysis.resolveFieldDelays();
            return true;
        }
        return false;
    }

    private boolean checkUnusedParameter() {
        StatementAnalysis lastStatementAnalysis = analysisProvider.getMethodAnalysis(parameterInfo.owner).getLastStatement();
        VariableInfo vi = lastStatementAnalysis == null ? null :
                lastStatementAnalysis.findOrNull(parameterInfo, VariableInfoContainer.Level.MERGE);
        if (vi == null || !vi.isRead()) {
            // unused variable
            parameterAnalysis.setProperty(VariableProperty.MODIFIED, Level.FALSE);
            parameterAnalysis.setProperty(VariableProperty.NOT_NULL, MultiLevel.NULLABLE);
            parameterAnalysis.setProperty(VariableProperty.NOT_MODIFIED_1, Level.FALSE);

            if (lastStatementAnalysis != null && parameterInfo.owner.isNotOverridingAnyOtherMethod()) {
                messages.add(Message.newMessage(new Location(parameterInfo.owner), Message.UNUSED_PARAMETER, parameterInfo.simpleName()));
            }

            parameterAnalysis.resolveFieldDelays();
            return true;
        }
        return false;
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
