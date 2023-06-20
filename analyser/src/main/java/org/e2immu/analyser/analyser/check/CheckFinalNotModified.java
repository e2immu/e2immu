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

import java.util.function.Function;

import static org.e2immu.analyser.parser.E2ImmuAnnotationExpressions.AFTER;

public class CheckFinalNotModified {

    /*
    creation of the @Final(after = ) annotations is in AbstractAnalysisBuilder
     */
    public static Message check(FieldInfo fieldInfo,
                                AnnotationExpression annotationKey,
                                FieldAnalysis fieldAnalysis) {
        Function<AnnotationExpression, String> extractInspected = ae -> ae.extract(AFTER, null);
        AnnotationExpression inAnalysis = fieldAnalysis.annotationGetOrDefaultNull(annotationKey);
        String mark = inAnalysis == null ? null : inAnalysis.extract(AFTER, null);

        return CheckHelper.checkAnnotationWithValue(fieldAnalysis,
                annotationKey,
                AFTER,
                extractInspected,
                mark,
                fieldInfo.fieldInspection.get().getAnnotations(),
                fieldInfo.newLocation());
    }
}
