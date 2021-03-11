/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.FieldAnalysisImpl;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.Final;
import org.e2immu.annotation.NotModified;

import java.util.function.Function;

public class CheckFinalNotModified {

    /*
    creation of the @Final(after = ) annotations is in AbstractAnalysisBuilder
     */
    public static void check(Messages messages,
                             FieldInfo fieldInfo,
                             Class<?> annotation,
                             AnnotationExpression annotationExpression,
                             FieldAnalysisImpl.Builder fieldAnalysis,
                             TypeAnalysis typeAnalysis) {

        Function<AnnotationExpression, String> extractInspected = ae -> ae.extract("after", null);
        boolean isModifiedOrVariable;
        if (Final.class.equals(annotation)) {
            isModifiedOrVariable = fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.FALSE;
        } else if (NotModified.class.equals(annotation)) {
            isModifiedOrVariable = fieldAnalysis.getProperty(VariableProperty.MODIFIED_METHOD) == Level.FALSE;
        } else throw new UnsupportedOperationException();

        String mark = typeAnalysis.isEventual() && isModifiedOrVariable ? typeAnalysis.markLabel() : null;

        CheckLinks.checkAnnotationWithValue(messages,
                fieldAnalysis,
                annotation.getName(),
                "@" + annotation.getSimpleName(),
                annotationExpression.typeInfo(),
                extractInspected,
                mark,
                fieldInfo.fieldInspection.get().getAnnotations(),
                new Location(fieldInfo));
    }
}
