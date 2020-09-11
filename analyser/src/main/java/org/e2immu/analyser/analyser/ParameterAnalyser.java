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

import org.e2immu.analyser.analyser.check.CheckSize;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.*;

import java.util.List;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.SIZE;
import static org.e2immu.analyser.util.Logger.log;

public class ParameterAnalyser {
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Messages messages = new Messages();

    public ParameterAnalyser(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
    }

    public void check(ParameterInfo parameterInfo) {
        // before we check, we copy the properties into annotations
        parameterInfo.parameterAnalysis.get().transferPropertiesToAnnotations(e2ImmuAnnotationExpressions);

        log(ANALYSER, "Checking parameter {}", parameterInfo.detailedString());

        check(parameterInfo, NotModified.class, e2ImmuAnnotationExpressions.notModified.get());
        check(parameterInfo, NotNull.class, List.of(e2ImmuAnnotationExpressions.notNull.get(),
                e2ImmuAnnotationExpressions.notNull1.get(),
                e2ImmuAnnotationExpressions.notNull2.get()));
        check(parameterInfo, NotNull1.class, List.of(e2ImmuAnnotationExpressions.notNull1.get(), e2ImmuAnnotationExpressions.notNull2.get()));
        check(parameterInfo, NotNull2.class, e2ImmuAnnotationExpressions.notNull2.get());
        check(parameterInfo, NotModified1.class, e2ImmuAnnotationExpressions.notModified1.get());

        // opposites
        check(parameterInfo, Nullable.class, e2ImmuAnnotationExpressions.nullable.get());
        check(parameterInfo, Modified.class, e2ImmuAnnotationExpressions.modified.get());

        CheckSize.checkSizeForParameters(messages, parameterInfo);
    }

    private void check(ParameterInfo parameterInfo, Class<?> annotation, List<AnnotationExpression> annotationExpressions) {
        parameterInfo.error(annotation, annotationExpressions).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(parameterInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    private void check(ParameterInfo parameterInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        parameterInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(parameterInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    /**
     * The goal is to ensure that NOT_NULL and SIZE are not unnecessarily delayed. NOT_MODIFIED will be set by the link computer
     * as soon as possible.
     *
     * @param methodProperties evaluation context
     * @return true if changes were made
     */
    public boolean analyse(VariableProperties methodProperties) {
        boolean changed = false;
        for (ParameterInfo parameterInfo : methodProperties.getCurrentMethod().methodInspection.get().parameters) {
            ParameterAnalysis parameterAnalysis = parameterInfo.parameterAnalysis.get();
            if (parameterAnalysis.assignedToField.isSet() && !parameterAnalysis.copiedFromFieldToParameters.isSet()) {
                FieldInfo fieldInfo = parameterAnalysis.assignedToField.get();
                FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.get();
                boolean delays = false;
                for (VariableProperty variableProperty : VariableProperty.FROM_FIELD_TO_PARAMETER) {
                    int inField = fieldAnalysis.getProperty(variableProperty);
                    if (inField != Level.DELAY) {
                        int inParameter = parameterAnalysis.getProperty(variableProperty);
                        if (inField > inParameter && verifySizeNotModified(variableProperty, parameterInfo, parameterAnalysis)) {
                            log(ANALYSER, "Copying value {} from field {} to parameter {} for property {}", inField,
                                    fieldInfo.fullyQualifiedName(), parameterInfo.detailedString(), variableProperty);
                            parameterAnalysis.setProperty(variableProperty, inField);
                            changed = true;
                        }
                    } else {
                        log(ANALYSER, "Still delaying copiedFromFieldToParameters because of {}", variableProperty);
                        delays = true;
                    }
                }
                if (!delays) {
                    log(ANALYSER, "No delays anymore on copying from field to parameter");
                    parameterAnalysis.copiedFromFieldToParameters.set(true);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * we only copy SIZE when it is also not MODIFIED!
     */
    private static boolean verifySizeNotModified(VariableProperty variableProperty, ParameterInfo parameterInfo, ParameterAnalysis parameterAnalysis) {
        if (variableProperty != VariableProperty.SIZE) return true;
        int modified = parameterAnalysis.getProperty(VariableProperty.MODIFIED);
        boolean accept = modified != Level.TRUE;
        log(SIZE, "To copy the SIZE property on {}, we look at MODIFIED. Copy? {}", parameterInfo.detailedString(), accept);
        return accept;
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
