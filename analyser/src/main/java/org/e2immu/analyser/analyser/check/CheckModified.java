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

import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.parser.Message;

import java.util.function.Function;

import static org.e2immu.analyser.parser.E2ImmuAnnotationExpressions.CONSTRUCTION;

public class CheckModified {

    /*
    Test the "construction" flag
     */
    public static Message check(MethodInfo methodInfo,
                                AnnotationExpression annotationKey,
                                MethodAnalysis methodAnalysis) {
        Function<AnnotationExpression, String> extractInspected =
                ae -> ae.extract(CONSTRUCTION, false).toString();
        AnnotationExpression inAnalysis = methodAnalysis.annotationGetOrDefaultNull(annotationKey);
        String construction = inAnalysis == null ? "false" :
                inAnalysis.extract(CONSTRUCTION, false).toString();

        return CheckHelper.checkAnnotationWithValue(methodAnalysis,
                annotationKey,
                CONSTRUCTION,
                extractInspected,
                construction,
                methodInfo.methodInspection.get().getAnnotations(),
                methodInfo.newLocation());
    }
}
