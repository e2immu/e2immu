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
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Lazy;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Size;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class ParameterAnalyser {
    private final TypeContext typeContext;

    public ParameterAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
    }

    public void check(ParameterInfo parameterInfo) {
        // before we check, we copy the properties into annotations
        parameterInfo.parameterAnalysis.get().transferPropertiesToAnnotations(typeContext);

        log(ANALYSER, "Checking parameter {}", parameterInfo.detailedString());

        Lazy<String> where = new Lazy<>(() -> "In method " +
                parameterInfo.parameterInspection.get().owner.fullyQualifiedName() + ", " +
                parameterInfo.detailedString());
        check(parameterInfo, NotModified.class, typeContext.notModified.get());
        check(parameterInfo, NotNull.class, typeContext.notNull.get()); // TODO check @NotNull1, 2
        CheckSize.checkSizeForParameters(typeContext, parameterInfo);
    }

    private void check(ParameterInfo parameterInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        parameterInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(parameterInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            typeContext.addMessage(error);
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
        // TODO
        return false;
    }
}
