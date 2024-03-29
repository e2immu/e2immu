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

package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.parser.Message;

import java.util.Map;
import java.util.function.Function;

public class CheckFinalNotModified {

    /*
    creation of the @Final(after = ) annotations is in AbstractAnalysisBuilder
     */
    public static Message check(FieldInfo fieldInfo,
                                Class<?> annotation,
                                AnnotationExpression annotationExpression,
                                FieldAnalysis fieldAnalysis) {
        Function<AnnotationExpression, String> extractInspected = ae -> ae.extract("after", null);
        Map.Entry<AnnotationExpression, Boolean> inAnalysis = fieldAnalysis.findAnnotation(annotation.getCanonicalName());
        String mark = inAnalysis == null ? null : inAnalysis.getKey().extract("after", null);

        return CheckLinks.checkAnnotationWithValue(fieldAnalysis,
                annotation.getName(),
                "@" + annotation.getSimpleName(),
                annotationExpression.typeInfo(),
                extractInspected,
                mark,
                fieldInfo.fieldInspection.get().getAnnotations(),
                fieldInfo.newLocation());
    }
}
