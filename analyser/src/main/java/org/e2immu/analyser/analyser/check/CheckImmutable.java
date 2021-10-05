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

import org.e2immu.analyser.analyser.AbstractAnalysisBuilder;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class CheckImmutable {

    public static void check(Messages messages,
                             WithInspectionAndAnalysis info,
                             Class<?> annotation,
                             AnnotationExpression annotationExpression,
                             AbstractAnalysisBuilder analysis,
                             boolean after,
                             boolean level,
                             boolean recursive) {
        List<CheckLinks.AnnotationKV> kvs = new ArrayList<>(3);

        if (after) {
            TypeAnalysis typeAnalysis = (TypeAnalysis) analysis;
            Function<AnnotationExpression, String> extractInspected1 = ae -> ae.extract("after", null);
            String value1 = typeAnalysis.isEventual() ? typeAnalysis.markLabel() : null;
            kvs.add(new CheckLinks.AnnotationKV(extractInspected1, value1));
        }

        if (level) {
            Function<AnnotationExpression, String> extractInspected2 = ae -> ae.extract("level", null);
            String value2 = CheckIndependent.levelString(analysis, VariableProperty.IMMUTABLE);
            kvs.add(new CheckLinks.AnnotationKV(extractInspected2, value2));
        }

        if (recursive) {
            Function<AnnotationExpression, String> extractInspected3 = ae -> {
                Boolean b = ae.extract("recursive", null);
                return b != null && b ? "true" : null;
            };
            String value3 = recursive(analysis);
            kvs.add(new CheckLinks.AnnotationKV(extractInspected3, value3));
        }

        CheckLinks.checkAnnotationWithValue(messages,
                analysis,
                annotation.getName(),
                "@" + annotation.getSimpleName(),
                annotationExpression.typeInfo(),
                kvs,
                info.getInspection().getAnnotations(),
                new Location(info));
    }

    private static String recursive(AbstractAnalysisBuilder analysis) {
        int immutable = analysis.getProperty(VariableProperty.IMMUTABLE);
        if (MultiLevel.level(immutable) == MultiLevel.MAX_LEVEL) return "true";
        return null;
    }
}
