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

import org.e2immu.analyser.analyser.TypeAnalysisImpl;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.Messages;

import java.util.function.Function;

public class CheckEventual {

    /*
    creation of the @E1Container, @E1Immutable, @E2Container, @E2Immutable annotations is in AbstractAnalysisBuilder
     */
    public static void check(Messages messages,
                             TypeInfo typeInfo,
                             Class<?> annotation,
                             AnnotationExpression annotationExpression,
                             TypeAnalysisImpl.Builder typeAnalysis) {

        Function<AnnotationExpression, String> extractInspected = ae -> ae.extract("after", null);
        String mark = typeAnalysis.isEventual() ? typeAnalysis.allLabelsRequiredForImmutable() : null;

        CheckLinks.checkAnnotationWithValue(messages,
                typeAnalysis,
                annotation.getName(),
                "@" + annotation.getSimpleName(),
                annotationExpression.typeInfo(),
                extractInspected,
                mark,
                typeInfo.typeInspection.get().getAnnotations(),
                new Location(typeInfo));
    }
}
